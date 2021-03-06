package org.xanho.cube.akka

import akka.actor.{Actor, ActorLogging, Props}
import com.seancheatham.graph.adapters.memory.MutableGraph
import com.seancheatham.storage.firebase.{ChildAddedHandler, FirebaseDatabase}
import org.xanho.cube.core.{Cube, Message}
import org.xanho.utility.FutureUtility.FutureHelper
import org.xanho.utility.data.Buckets
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

/**
  * An Actor which maintains a specific Cube.  This Actor is responsible for cube messaging and cube dreaming.
  *
  * @param cubeId The ID of the cube to instantiate
  */
class CubeActor(cubeId: String) extends Actor with ActorLogging {

  import context.dispatcher

  private val startTime =
    System.currentTimeMillis()

  /**
    * This Actor's corresponding cube.  When the actor is initialized, it must fetch the cube
    * from storage, which may take a while.  This step will block until complete.
    */
  private var cube: Cube =
    FirebaseDatabase()
      .get(Buckets.CUBES, cubeId, "graph")
      .map(_.as[MutableGraph](MutableGraph.read()))
      .recover {
        case _: NoSuchElementException =>
          new MutableGraph()
      }
      .zip(
        FirebaseDatabase()
          .get(Buckets.CUBES, cubeId, "data")
          .map(_.as[Map[String, JsValue]])
      )
      .map {
        case (graph, data) =>
          Cube(cubeId, Seq.empty, graph, data)((dest, text) => sendMessage(dest, text))
      }
      .await

  /**
    * The ID of the listener attached to this Cube's message storage in the database
    */
  private var messageListenerId: Option[String] =
    None

  /**
    * Attach the message listener right away
    */
  attachMessageListener()

  /**
    * Every 5 minutes, save the cube
    */
  context.system.scheduler.schedule(5.minutes, 5.minutes)(self ! CubeActor.Messages.SaveData)

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
  def receive: Receive = {
    case message: Message if message.timestamp > startTime =>
      cube = cube receive message

    case message: Message =>
      cube = cube appended message

    case Messages.Status =>
      sender() ! Messages.Ok

    case CubeActor.Messages.SaveData =>
      saveCube().await
  }

  /**
    * Detach the message listener, save the cube, and respond with an Ok
    */
  override def postStop(): Unit = {
    detachMessageListener()
    saveCube()
      .andThen {
        case Success(_) =>
          sender() ! Messages.Ok
        case _ =>
          sender() ! Messages.NotOk
      }
      .await(Duration.Inf)
  }

  /**
    * Attach a listener to this cube's message list in the database.
    * Upon each new message, self-send it to this Actor
    */
  private def attachMessageListener() =
    messageListenerId =
      Some(
        FirebaseDatabase().watchCollection("cubes", cubeId, "messages")(
          ChildAddedHandler((_: String, v: JsValue) => self ! v.as[Message])
        )()
      )

  /**
    * Detach the message listener
    */
  private def detachMessageListener() =
    messageListenerId
      .foreach { id =>
        FirebaseDatabase().unwatchCollection(id)
        messageListenerId = None
      }

  /**
    * Save the cube's graph and data to document storage
    */
  private def saveCube(): Future[_] =
    FirebaseDatabase()
      .write(Buckets.CUBES, cubeId, "graph")(Json.toJson(cube.graph))
      .zip(
        FirebaseDatabase()
          .write(Buckets.CUBES, cubeId, "data")(JsObject(cube.data))
      )

  /**
    * Sends a message from this cube to the destination, retrying X number of times in case of failure
    *
    * @param destination The destination ID (user or Cube)
    * @param text        The text to send
    * @param retries     The number of retries left before throwing an exception
    * @return a Future result
    */
  private def sendMessage(destination: String,
                          text: String,
                          retries: Int = 3): Future[_] = {
    val f =
      FirebaseDatabase().append(Buckets.CUBES, cubeId, "messages")(
        Json.toJson(Message(text, cubeId, destination, System.currentTimeMillis()))
      )
    f onFailure {
      case t =>
        if (retries <= 0)
          throw t
        else
          sendMessage(destination, text, retries - 1)
    }
    f
  }
}

object CubeActor {

  def props(cubeId: String): Props =
    Props(new CubeActor(cubeId))

  object Messages {

    case object SaveData extends ActorMessage

  }

}