package org.xanho.cube.core

import com.seancheatham.graph.Graph
import com.seancheatham.graph.adapters.memory.MutableGraph
import play.api.libs.json.JsValue

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/**
  * The basic abstraction of a Xanho Cube.  A Cube represents a mini-computer which has an ID, stores a series
  * of messages, and an internal knowledge graph.
  */
sealed abstract class Cube {

  /**
    * The unique identifier of this Cube
    */
  def id: String

  /**
    * The internal knowledge graph of this cube
    */
  def graph: Graph

  /**
    * The sequence of messages received by or sent from this Cube
    */
  def messages: Seq[Message]

  /**
    * Additional meta-data of this Cube
    */
  def data: Map[String, JsValue]

  /**
    * Transforms this Cube into a [[org.xanho.cube.core.DreamingCube]]
    */
  def dream: DreamingCube =
    DreamingCube(id, graph, messages, data)

  /**
    * Transforms this Cube into an [[org.xanho.cube.core.InteractiveCube]]
    *
    * @return
    */
  def interact: InteractiveCube =
    InteractiveCube(id, graph, messages, data)

}

object Cube {

  import play.api.libs.json._

  implicit def read: Reads[Cube] =
    Reads[Cube](
      v =>
        JsSuccess(
          InteractiveCube(
            (v \ "id").as[String],
            (v \ "graph").as[MutableGraph](MutableGraph.read()),
            (v \ "messages").as[Seq[Message]],
            (v \ "data").as[Map[String, JsValue]]
          )
        )
    )

  implicit val write: Writes[Cube] =
    Writes[Cube](cube =>
      Json.obj(
        "id" -> cube.id,
        "graph" -> cube.graph,
        "messages" -> cube.messages,
        "data" -> cube.data
      )
    )
}

/**
  * A Cube which is in an interactive state.  When interactive, the Cube can receive messages, and optionally respond.
  *
  * @param id       @see [[org.xanho.cube.core.Cube#id]]
  * @param graph    @see [[org.xanho.cube.core.Cube#graph]]
  * @param messages @see [[org.xanho.cube.core.Cube#messageHistory]]
  * @param data     @see [[org.xanho.cube.core.Cube#data]]
  */
case class InteractiveCube(id: String,
                           graph: Graph,
                           messages: Seq[Message],
                           data: Map[String, JsValue]) extends Cube {

  /**
    * Receives the provided message, attempts to construct a response, and returns a new cube
    *
    * @param message The inbound [[org.xanho.cube.core.Message]]
    * @return a tuple (an updated [[org.xanho.cube.core.InteractiveCube]], an optional response [[org.xanho.cube.core.Message]])
    */
  def receive(message: Message): (InteractiveCube, Option[Message]) = {
    val response =
      constructResponse(message)
    val withMessage =
      this :+ message
    val newCube =
      response.fold(withMessage)(withMessage :+ _)
    (newCube, response)
  }

  private def constructResponse(input: Message): Option[Message] =
    ???

  protected def :+(message: Message): InteractiveCube =
    InteractiveCube(id, graph, messages :+ message, data)

  override def interact: InteractiveCube =
    this
}

/**
  * A Cube which is in a dreaming state.  When dreaming, the Cube processes all of its messages over and over,
  * updating its internal knowledge graph along the way.  This Cube runs a background Future which performs this process.
  * When awoken, a new cube with an updated graph is returned.
  *
  * @param id       @see [[org.xanho.cube.core.Cube#id]]
  * @param graph    @see [[org.xanho.cube.core.Cube#graph]]
  * @param messages @see [[org.xanho.cube.core.Cube#messageHistory]]
  */
case class DreamingCube(id: String,
                        graph: Graph,
                        messages: Seq[Message],
                        data: Map[String, JsValue]) extends Cube {

  import scala.concurrent.ExecutionContext.Implicits.global

  private var continue =
    true

  private val future =
    Future {
      var g =
        graph
      while (continue) {
        // TODO
      }
      g
    }

  override def dream: DreamingCube =
    this

  /**
    * Awakens this Dreaming Cube by signalling the background future, and awaiting its result
    */
  override def interact: InteractiveCube = {
    continue = false
    import scala.concurrent.ExecutionContext.Implicits.global
    Await.result(
      future map (InteractiveCube(id, _, messages, data)),
      Duration.Inf
    )
  }

}