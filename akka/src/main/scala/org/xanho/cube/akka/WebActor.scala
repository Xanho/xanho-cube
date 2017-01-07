package org.xanho.cube.akka

import java.util.UUID

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer

/**
  * An Actor to be used as a companion to a Web Backend server, providing Actor System access
  * to the backend server.
  */
class WebActor(id: String,
               receiveHandler: Receive,
               shutdownHandler: () => Unit) extends Actor with ActorLogging {

  implicit val materializer =
    ActorMaterializer()

  /**
    * Obligatory Akka Actor Receive method
    */
  def receive: Receive = {
    val internal: Receive = {
      case Messages.Status =>
        sender() ! Messages.Ok
    }

    internal.orElse(receiveHandler)
  }

  /**
    * Shuts down this HTTP server by calling the necessary Akka shutdown functions.  This call will block
    * until the server binding completes its shutdown.
    */
  override def postStop(): Unit =
    shutdownHandler()

}

import com.typesafe.scalalogging.LazyLogging

object WebActor extends LazyLogging {
  def props(id: String,
            receiveHandler: Receive,
            shutdownHandler: () => Unit): Props =
    Props(new WebActor(id, receiveHandler, shutdownHandler))

  def initialize(id: String = UUID.randomUUID().toString,
                 receiveHandler: Receive,
                 shutdownHandler: () => Unit)
                (implicit system: ActorSystem = defaultSystem): (ActorRef, ActorSystem) = {
    logger.info(s"Starting a Web actor with ID $id")
    val actorRef =
      system.actorOf(props(id, receiveHandler, shutdownHandler), s"$webPrefix$id")
    (actorRef, system)
  }
}
