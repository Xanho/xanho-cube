package org.xanho.cube.akka

import java.util.UUID

import akka.NotUsed
import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.ByteString
import com.seancheatham.graph.adapters.memory.MutableGraph
import com.seancheatham.storage.firebase.FirebaseDatabase
import org.xanho.cube.akka.rpc.WebSocketActor
import org.xanho.utility.FutureUtility.FutureHelper
import org.xanho.utility.data.Buckets
import org.xanho.web.rpc.Messages.RPCMessage
import play.api.libs.json.{JsString, JsValue, Json}

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Success

/**
  * An Actor which serves as an API/HTTP Server, which enables an HTTP entrypoint into the system
  *
  * @param id   The unique ID of this API Server
  * @param host The host to bind to
  * @param port The port to bind to
  */
class ApiActor(id: String,
               host: String,
               port: Int) extends Actor with ActorLogging {

  import context.dispatcher

  implicit val materializer =
    ActorMaterializer()

  /**
    * The routes for this API
    */
  private val routes: Route = {

    val wsRoute =
      path("ws")(
        get(handleWebSocketMessages(webSocket()))
      )

    val cubeRoutes =
      path("cubes" / Segment / "mount")(cubeId =>
        onSuccess(mountCube(cubeId))(_ => complete(StatusCodes.OK))
      ) ~
        path("cubes" / Segment / "dismount")(cubeId =>
          onSuccess(dismountCube(cubeId))(_ => complete(StatusCodes.OK))
        )

    wsRoute ~
      cubeRoutes

  }

  /**
    * The HTTP binding for the server
    */
  private val binding: Future[ServerBinding] =
    Http()(context.system).bindAndHandle(routes, host, port)
      .map { binding =>
        log.info(s"Server online at http://$host:$port/")
        binding
      }

  /**
    * Obligatory Akka Actor Receive method
    */
  def receive: Receive = {

    case Messages.Status =>
      sender() ! Messages.Ok

    case ApiActor.Messages.Register(userId) =>
      log.info(s"Received cube register request for user $userId")
      val s = sender()
      createUser(userId)
        .onComplete {
          case Success(_) =>
            s ! Messages.Ok
          case _ =>
            Messages.NotOk
        }
  }

  /**
    * Shuts down this HTTP server by calling the necessary Akka shutdown functions.  This call will block
    * until the server binding completes its shutdown.
    */
  override def postStop(): Unit =
    binding
      .flatMap(_.unbind())
      .await(Duration.Inf)

  private def createUser(userId: String): Future[String] = {
    FirebaseDatabase.default
      .get("users", userId)
      .recoverWith {
        case _: NoSuchElementException =>
          createCube(userId)
            .flatMap(cubeId =>
              FirebaseDatabase.default.write("users", userId, "cubeId")(JsString(cubeId))
            )
      }
      .map(_ => userId)
  }

  /**
    * Creates a new Cube in the database
    *
    * @return The ID of the new cube
    */
  private def createCube(ownerId: String): Future[String] = {
    val id =
      UUID.randomUUID().toString
    FirebaseDatabase.default
      .write(Buckets.CUBES, id)(
        Json.obj(
          "messages" -> Seq.empty[JsValue],
          "graph" -> new MutableGraph()(),
          "data" -> Json.obj(
            "creationDate" -> System.currentTimeMillis(),
            "ownerId" -> ownerId
          )
        )
      )
      .flatMap(_ => mountCube(id))
      .map(_ => id)
  }

  private def mountCube(cubeId: String): Future[_] =
    CubeMaster.ref
      .ask(CubeMaster.Messages.Mount(Set(cubeId)))
      .filter(_ == Messages.Ok)

  private def dismountCube(cubeId: String): Future[_] =
    CubeMaster.ref
      .ask(CubeMaster.Messages.Dismount(Set(cubeId)))
      .filter(_ == Messages.Ok)

  private def webSocket(): Flow[Message, Message, NotUsed] = {
    val actor =
      context.system.actorOf(Props(new WebSocketActor(self)))
    import upickle.Js
    import upickle.default.write

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case TextMessage.Strict(text) =>
          val json =
            Json.parse(text)
          import org.xanho.cube.akka.rpc.playToUPickle
          val message =
            RPCMessage.read(playToUPickle(json).asInstanceOf[Js.Obj])
          WebSocketActor.Messages.Receive(message)
        case TextMessage.Streamed(textStream) =>
          ???
        case BinaryMessage.Strict(data: ByteString) =>
          ???
        case BinaryMessage.Streamed(dataStream) =>
          ???
      }.to(Sink.actorRef(actor, PoisonPill))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[RPCMessage](10, OverflowStrategy.fail)
        .mapMaterializedValue {
          outActor =>
            actor ! WebSocketActor.Messages.Connected(outActor)
            NotUsed
        }.map(
        rpcMessage =>
          TextMessage(
            write[RPCMessage](rpcMessage)(org.xanho.web.rpc.Messages.writeRPCMessage)
          )
      )

    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }
}

import com.typesafe.scalalogging.LazyLogging

object ApiActor extends LazyLogging {

  def initialize(host: String,
                 port: Int)
                (implicit system: ActorSystem = defaultSystem): ActorRef = {
    logger.info(s"Starting an API actor")
    val id = UUID.randomUUID().toString
    system.actorOf(
      ApiActor.props(id, host, port),
      s"api-$id"
    )
  }

  def props(id: String,
            host: String,
            port: Int): Props =
    Props(new ApiActor(id, host, port))


  object Messages {

    case class Register(userId: String) extends ActorMessage

  }

}

object ApiRouter extends LazyLogging {

  def initialize(initialSize: Int = 1,
                 host: String,
                 port: Int)
                (implicit system: ActorSystem = defaultSystem): ActorRef = {
    logger.info(s"Starting an API Router actor")
    system.actorOf(
      RoundRobinPool(initialSize)
        .props(
          ApiActor.props(UUID.randomUUID().toString, host, port)
        ),
      "api"
    )
  }

  def ref(implicit context: ActorContext): Future[ActorRef] =
    context.actorSelection(apiPath).resolveOne()

}
