package org.xanho.cube.akka

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow

/**
  * An Actor which serves as an API/HTTP Server, which enables an HTTP entrypoint into the system
  * @param id The unique ID of this API Server
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
    Await.result(context.actorSelection(masterPath).resolveOne(), defaultTimeout)

  implicit val materializer =
    ActorMaterializer()

  private val testAccountId: String =
    "abcd1234"

  /**
    * The routes for this API
    */
  private val routes: Route =
    path("cubes")(
      post(
        onSuccess(
          cubeMaster ? CubeMaster.Messages.CreateCube(testAccountId) map (_.asInstanceOf[String])
        )(complete(_))
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
    Await.ready(
      binding
        .flatMap(_.unbind()),
      Duration.Inf
    )
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
