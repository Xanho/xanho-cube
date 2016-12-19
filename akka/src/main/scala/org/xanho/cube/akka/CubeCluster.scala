package org.xanho.cube.akka

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import org.xanho.cube.core.Message

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Success

class CubeCluster extends Actor {

  import context.dispatcher

  private val cubeActors: mutable.Map[String, ActorRef] =
    mutable.Map.empty

  def receive = {
    import CubeCluster.Messages._
    {
      case message@Message(_, _, destination, _) =>
        if (isUser(destination))
          sendToUser(message)
        else
          cubeActors.get(message.destinationId)
            .fold(
              sender() ! Messages.NotFound(message.destinationId)
            )(_ forward message)

      case Register(cubeIds) =>
        cubeIds.foreach(loadCube)
        sender() ! Messages.Ok

      case Unregister(cubeIds) =>
        Await.ready(
          Future.traverse(cubeIds)(unloadCube)
            .andThen {
              case Success(_) =>
                sender() ! Messages.Ok
              case _ =>
                sender() ! Messages.NotOk
            },
          Duration.Inf
        )

      case UnregisterAll =>
        Await.ready(
          Future.traverse(cubeActors.keysIterator)(unloadCube)
            .andThen {
              case Success(_) =>
                sender() ! Messages.Ok
              case _ =>
                sender() ! Messages.NotOk
            },
          Duration.Inf
        )

      case Messages.Status =>
        import scala.concurrent.duration._
        implicit val askTimeout =
          Timeout(10.seconds)
        Future.traverse(cubeActors)(kv => (kv._2 ? Messages.Status) map (kv._1 -> _))
          .onComplete {
            case Success(status) =>
              sender() ! status.toMap
            case _ =>
              Messages.NotOk
          }
    }
  }

  private def sendToUser(message: Message): Unit =
    ???

  private def isUser(id: String): Boolean =
    ???

  private def loadCube(cubeId: String): ActorRef =
    cubeActors.getOrElseUpdate(cubeId, context.actorOf(Props[CubeActor], cubeId))

  private def unloadCube(cubeId: String): Future[_] = {
    import scala.concurrent.duration._
    implicit val askTimeout =
      Timeout(30.seconds)
    cubeActors.get(cubeId)
      .map(_ ? Messages.TerminateGracefully)
      .map(_.andThen { case _ => cubeActors.remove(cubeId) })
      .getOrElse(Future())
  }
}

object CubeCluster {

  object Messages {

    case class Register(cubeIds: Seq[String])

    case class Unregister(cubeIds: Seq[String])

    case object UnregisterAll

  }

}