package org.xanho.cube.akka

import java.util.UUID

import akka.NotUsed
import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.routing.RoundRobinPool
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.ByteString
import org.xanho.cube.akka.rpc.WebSocketActor
import org.xanho.utility.FutureUtility.FutureHelper
import org.xanho.web.rpc.Messages.RPCMessage
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration.Duration

/**
  * An Actor to be used as a companion to a Web Backend server, providing Actor System access
  * to the backend server.
  */
class WebActor(id: String,
               host: String,
               port: Int,
               resourceBasePath: String) extends Actor with ActorLogging {

  import context.dispatcher

  implicit val materializer =
    ActorMaterializer()

  /**
    * Obligatory Akka Actor Receive method
    */
  def receive: Receive = {
    case Messages.Status =>
      sender() ! Messages.Ok
  }

  private val routes: Route =
    path("ws")(
      get(handleWebSocketMessages(webSocket()))
    ) ~
      get(
        pathSingleSlash(
          encodeResponse(
            getFromFile(resourceBasePath + "index.html")
          )
        ) ~
          encodeResponse(
            getFromDirectory(resourceBasePath)
          )
      )


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
    * Shuts down this HTTP server by calling the necessary Akka shutdown functions.  This call will block
    * until the server binding completes its shutdown.
    */
  override def postStop(): Unit =
    binding
      .flatMap(_.unbind())
      .await(Duration.Inf)

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

object WebActor extends LazyLogging {

  def props(id: String,
            host: String,
            port: Int,
            resourceBasePath: String): Props =
    Props(new WebActor(id, host, port, resourceBasePath))

  def initialize(id: String = UUID.randomUUID().toString,
                 host: String,
                 port: Int,
                 resourceBasePath: String = "")
                (implicit system: ActorSystem = defaultSystem): (ActorRef, ActorSystem) = {
    logger.info(s"Starting a Web actor with ID $id")
    val actorRef =
      system.actorOf(props(id, host, port, resourceBasePath), s"$webPrefix$id")
    (actorRef, system)
  }
}

object WebRouter extends LazyLogging {

  def initialize(initialSize: Int = 1,
                 host: String,
                 port: Int,
                 resourceBasePath: String)
                (implicit system: ActorSystem = defaultSystem): ActorRef = {
    logger.info(s"Starting a Web Router actor")
    system.actorOf(
      RoundRobinPool(initialSize)
        .props(
          WebActor.props(UUID.randomUUID().toString, host, port, resourceBasePath)
        ),
      "web"
    )
  }

  def ref(implicit context: ActorContext): Future[ActorRef] =
    context.actorSelection(webPath).resolveOne()

}
