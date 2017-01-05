package org.xanho.cube.akka

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.pattern.ask
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
import org.xanho.cube.akka.api.{authenticator, models, realm}
import org.xanho.utility.FutureUtility.FutureHelper
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.concurrent.Future
import scala.concurrent.duration.Duration

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
    context.actorSelection(masterPath).resolveOne().await

  implicit val materializer =
    ActorMaterializer()

  private val testAccountId: String =
    "abcd1234"

  /**
    * The routes for this API
    */
  private val routes: Route = {

    val userRoutes =
      authenticateOAuth2Async(realm, authenticator) {
        case (token: String, userId: String, userEmail: String) =>
          path("users" / Segment)(id =>
            if (userId != id)
              complete(StatusCodes.Unauthorized)
            else
              get(
                onSuccess(models.User.get(userId))(
                  _.fold(complete(StatusCodes.NotFound))(complete(_))
                )
              ) ~
                post(
                  entity(as[Map[String, JsValue]].map(JsObject(_).as[models.User]))(
                    user =>
                      onSuccess(
                        models.User.write(userId)(Json.toJson(user))
                          .flatMap(_ => createCube(userId))
                      )(_ => complete(StatusCodes.Created))
                  )
                ) ~
                put(
                  entity(as[Map[String, JsValue]].map(JsObject(_).as[models.User]))(
                    user =>
                      onSuccess(
                        models.User.merge(userId)(Json.toJson(user))
                      )(_ => complete(StatusCodes.Created))
                  )
                )
          )
      }

    userRoutes

  }

  private def createCube(ownerId: String): Future[String] =
    cubeMaster ? CubeMaster.Messages.CreateCube(testAccountId) map (_.asInstanceOf[String])

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

  }

  /**
    * Shuts down this HTTP server by calling the necessary Akka shutdown functions.  This call will block
    * until the server binding completes its shutdown.
    */
  override def postStop(): Unit =
    binding
      .flatMap(_.unbind())
      .await(Duration.Inf)
}

import com.typesafe.scalalogging.LazyLogging

object ApiActor extends LazyLogging {
  def props(id: String,
            host: String,
            port: Int): Props =
    Props(new ApiActor(id, host, port))

  def initialize(id: String = UUID.randomUUID().toString,
                 host: String,
                 port: Int): Unit = {
    logger.info(s"Starting an API actor with ID $id at $host:$port")
    val system =
      ActorSystem("xanho")

    system.actorOf(props(id, host, port), s"$apiPrefix$id")
  }
}
