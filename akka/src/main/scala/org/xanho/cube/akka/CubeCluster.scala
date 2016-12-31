package org.xanho.cube.akka

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Success

class CubeCluster(id: String) extends Actor {

  import context.dispatcher

  private val cubeActors: mutable.Map[String, ActorRef] =
    mutable.Map.empty

  def receive = {
    import CubeCluster.Messages._
    {

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
        Future.traverse(cubeActors)(kv => (kv._2 ? Messages.Status) map (kv._1 -> _))
          .onComplete {
            case Success(status) =>
              sender() ! status.toMap
            case _ =>
              Messages.NotOk
          }
    }
  }

  private def loadCube(cubeId: String): ActorRef =
    cubeActors.getOrElseUpdate(
      cubeId,
      context.actorOf(CubeActor.props(cubeId), cubeId)
    )

  override def postStop(): Unit =
    Future.traverse(cubeActors.keysIterator)(unloadCube)

  private def unloadCube(cubeId: String): Future[_] = {
    import akka.pattern.gracefulStop

    import scala.concurrent.duration._

    cubeActors.get(cubeId)
      .map(gracefulStop(_, 30.seconds))
      .map(_.andThen { case _ => cubeActors.remove(cubeId) })
      .getOrElse(Future())
  }
}

object CubeCluster {

  def props(id: String): Props =
    Props(new CubeCluster(id))

  object Messages {

    case class Register(cubeIds: Seq[String])

    case class Unregister(cubeIds: Seq[String])

    case object UnregisterAll

  }

}