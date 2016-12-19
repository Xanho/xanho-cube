package org.xanho.cube.akka

import akka.actor.Actor
import org.xanho.cube.akka.CubeActor.Messages.{DreamingState, InteractiveState}
import org.xanho.cube.core.{Cube, DreamingCube, InteractiveCube, Message}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

class CubeActor extends Actor {

  private var cube: Cube =
    ???

  private def cubeState =
    cube match {
      case _: InteractiveCube =>
        InteractiveState(cube.id)
      case _: DreamingCube =>
        DreamingState(cube.id)
    }

  private def lastUserMessageTime =
    cube.messages.last.timestamp

  context.system.scheduler.schedule(30.seconds, 30.seconds)(
    cube match {
      case _: DreamingCube =>
      case _ =>
        if (System.currentTimeMillis() - lastUserMessageTime > 30.seconds.length)
          cube = cube.dream
    }
  )

  def receive = {
    {
      case message@Message(text, source, _, timestamp) =>
        val (newCube, response) =
          cube.interact.receive(message)
        cube = newCube
        response.foreach(sender() ! _)

      case Messages.Status =>
        sender() ! cubeState

      case Messages.TerminateGracefully =>
        cube = cube.interact
        saveCube()
          .onComplete {
            case Success(_) =>
              sender() ! Messages.Ok
            case _ =>
              sender() ! Messages.NotOk
          }
    }
  }

  private def saveCube(): Future[_] =
    ???

}

object CubeActor {

  object Messages {

    case object SaveData

    sealed trait CubeActorState

    case class InteractiveState(cubeId: String) extends CubeActorState

    case class DreamingState(cubeId: String) extends CubeActorState

  }

}