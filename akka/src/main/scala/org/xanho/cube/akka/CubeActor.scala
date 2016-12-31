package org.xanho.cube.akka

import akka.actor.{Actor, Props}
import com.seancheatham.graph.adapters.memory.MutableGraph
import org.xanho.cube.core.{Cube, Message}
import org.xanho.utility.data.{Buckets, DocumentStorage, FirebaseDatabase}
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Success

/**
  * An Actor which maintains a specific Cube.  This Actor is responsible for cube messaging and cube dreaming.
  * If a message has not be received for X number of seconds, the Cube switches into a dream state where it
  * will do its "thinking"
  *
  * @param cubeId The ID of the cube to instantiate
  */
class CubeActor(cubeId: String) extends Actor {

  import context.dispatcher

  /**
    * This Actor's corresponding cube.  When the actor is initialized, it must fetch the cube
    * from storage, which may take a while.  This step will block until complete.
    */
  private var cube: Cube =
    Cube(
      cubeId,
      Seq.empty,
      Await.result(
        DocumentStorage.default
          .get(Buckets.CUBES, cubeId, "graph")
          .map(_.get.as[MutableGraph](MutableGraph.read())),
        defaultTimeout
      ),
      Await.result(
        DocumentStorage.default
          .get(Buckets.CUBES, cubeId, "data")
          .map(_.get.as[Map[String, JsValue]]),
        defaultTimeout
      )
    )((dest, text) => sendMessage(dest, text))

  /**
    * Every 5 minutes, save the cube
    */
  context.system.scheduler.schedule(5.minutes, 5.minutes)(self ! CubeActor.Messages.SaveData)

  /**
    * Attach the message listener right away
    */
  context.system.scheduler.scheduleOnce(0.minutes)(attachMessageListener())

  /**
    * Interprets the following messages:
    * Cube Message: Pass into cube, see if the cube has a response, and respond with it
    * Start Dreaming: Enable Dream State
    * Start Interacting: Enable Interactive State
    * Status: Response with current Cube Status
    * Save Data: Save the current Cube's graph/data
    * Terminate Gracefully: Shut down the cube and save it
    *
    * @return
    */
  def receive: PartialFunction[Any, Unit] = {
    case message: Message =>
      cube = cube receive message

    case Messages.Status =>
      sender() ! Messages.Ok

    case CubeActor.Messages.SaveData =>
      Await.result(saveCube(), defaultTimeout)
  }

  override def postStop(): Unit = {
    detachMessageListener()
    val f =
      saveCube()
        .andThen {
          case Success(_) =>
            sender() ! Messages.Ok
          case _ =>
            sender() ! Messages.NotOk
        }
    Await.ready(f, Duration.Inf)
  }

  /**
    * The ID of the listener attached to this Cube's message storage in the database
    */
  private var messageListenerId: Option[String] =
    None

  private def attachMessageListener() =
    messageListenerId =
      DocumentStorage.default match {
        case FirebaseDatabase =>
          Some(
            FirebaseDatabase.watchCollection("cubes", cubeId, "messages")((v: JsValue) => self ! v.as[Message])
          )
        case _ =>
          None
      }

  private def detachMessageListener() =
    messageListenerId
      .foreach(id =>
        DocumentStorage.default match {
          case FirebaseDatabase =>
            FirebaseDatabase.unwatchCollection(id)
            messageListenerId = None
          case _ =>
        }
      )

  /**
    * Save the cube's graph and data to document storage
    */
  private def saveCube(): Future[_] =
    Future.sequence(
      Vector(
        DocumentStorage.default
          .write(Buckets.CUBES, cubeId, "graph")(Json.toJson(cube.graph)),
        DocumentStorage.default
          .write(Buckets.CUBES, cubeId, "data")(JsObject(cube.data))
      )
    )

  private def sendMessage(destination: String,
                          text: String,
                          retries: Int = 3): Unit =
    DocumentStorage.default.append(Buckets.CUBES, "messages")(
      Json.toJson(Message(text, cubeId, destination, System.currentTimeMillis()))
    ) onFailure {
      case t =>
        if (retries <= 0)
          throw t
        else
          sendMessage(destination, text, retries - 1)
    }
}

object CubeActor {

  def props(cubeId: String): Props =
    Props(new CubeActor(cubeId))

  object Messages {

    case object SaveData

    case object StartDreaming

    case object StartInteracting

    sealed trait CubeActorState

    case class InteractiveState(cubeId: String) extends CubeActorState

    case class DreamingState(cubeId: String) extends CubeActorState

  }

}