package org.xanho.cube.akka

import java.util.UUID

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.stream.ActorMaterializer
import com.seancheatham.graph.adapters.memory.MutableGraph
import org.xanho.cube.core.Message
import org.xanho.utility.FutureUtility.FutureHelper
import org.xanho.utility.data.{Buckets, DocumentStorage}
import play.api.libs.json.{JsString, Json}

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

  /**
    * A reference to the Cube Master
    */
  private val cubeMaster: ActorRef =
    CubeMaster.ref.await

  implicit val materializer =
    ActorMaterializer()

  /**
    * The routes for this API
    */
  private val routes: Route = {

//    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    //    import org.xanho.cube.akka.api.{authenticator, models, realm}
//    val userRoutes =
//      authenticateOAuth2Async(realm, authenticator) {
//        case (token: String, userId: String, userEmail: String) =>
//          path("users" / Segment)(id =>
//            if (userId != id)
//              complete(StatusCodes.Unauthorized)
//            else
//              get(
//                onSuccess(models.User.get(userId))(
//                  _.fold(complete(StatusCodes.NotFound))(complete(_))
//                )
//              ) ~
//                post(
//                  entity(as[Map[String, JsValue]].map(JsObject(_).as[models.User]))(
//                    user =>
//                      onSuccess(
//                        self.ask(ApiActor.Messages.Register(userId))
//                          .map(_ => models.User.merge(userId)(Json.toJson(user)))
//                      )(_ => complete(StatusCodes.Created))
//                  )
//                ) ~
//                put(
//                  entity(as[Map[String, JsValue]].map(JsObject(_).as[models.User]))(
//                    user =>
//                      onSuccess(
//                        models.User.merge(userId)(Json.toJson(user))
//                      )(_ => complete(StatusCodes.Created))
//                  )
//                )
//          )
//      }

    val cubeRoutes =
      path("cubes" / Segment / "mount")(cubeId =>
        onSuccess(mountCube(cubeId))(_ => complete(StatusCodes.OK))
      ) ~
        path("cubes" / Segment / "dismount")(cubeId =>
          onSuccess(dismountCube(cubeId))(_ => complete(StatusCodes.OK))
        )

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
    DocumentStorage()
      .get("users", userId)
      .flatMap {
        case Some(_) =>
          Future.successful(userId)
        case _ =>
          createCube(userId)
            .flatMap(cubeId =>
              DocumentStorage().write("users", userId, "cubeId")(JsString(cubeId))
            )
            .map(_ => userId)
      }
  }

  /**
    * Creates a new Cube in the database
    *
    * @return The ID of the new cube
    */
  private def createCube(ownerId: String): Future[String] = {
    val id =
      UUID.randomUUID().toString
    DocumentStorage.default
      .write(Buckets.CUBES, id)(
        Json.obj(
          "messages" -> Seq.empty[Message],
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
    cubeMaster
      .ask(CubeMaster.Messages.Mount(Set(cubeId)))
      .filter(_ == Messages.Ok)

  private def dismountCube(cubeId: String): Future[_] =
    cubeMaster
      .ask(CubeMaster.Messages.Dismount(Set(cubeId)))
      .filter(_ == Messages.Ok)
}

import com.typesafe.scalalogging.LazyLogging

object ApiActor extends LazyLogging {

  def props(id: String,
            host: String,
            port: Int): Props =
    Props(new ApiActor(id, host, port))


  object Messages {

    case class Register(userId: String)

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
