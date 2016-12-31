package org.xanho.cube.core

import com.seancheatham.graph.Graph
import com.seancheatham.graph.adapters.memory.MutableGraph
import play.api.libs.json.JsValue


/**
  * The basic abstraction of a Xanho Cube.  A Cube represents a mini-computer which has an ID, stores a series
  * of messages, and an internal knowledge graph.
  * @param id The unique identifier of this Cube
  * @param messages The sequence of messages received by or sent from this Cube
  * @param graph The internal knowledge graph of this cube
  * @param data Additional meta-data of this Cube
  * @param sendMessage A function which, when given a tuple (Destination ID, Message Text),
  *                    sends a message to that entity
  */
case class Cube(id: String,
                messages: Seq[Message],
                graph: Graph,
                data: Map[String, JsValue])(sendMessage: (String, String) => Unit) {

  /**
    * Runs a "dream" cycle iteration, which produces a new Cube with an updated graph, as well as a sequence of tuples
    * (Destination ID, Message Text) indicating any messages the Cube would like to send to the user
    */
  def dream: (Cube, Seq[(String, String)]) =
    ???

  /**
    * Receives the provided message and adds it to this Cube's message list
    *
    * @param message The inbound [[org.xanho.cube.core.Message]]
    * @return an updated Cube
    */
  def receive(message: Message): Cube =
    copy(messages = messages :+ message)(sendMessage)

}

object Cube {

  import play.api.libs.json._

  def read(sendMessage: (String, String) => Unit): Reads[Cube] =
    Reads[Cube](
      v =>
        JsSuccess(
          Cube(
            (v \ "id").as[String],
            (v \ "messages").as[Seq[Message]],
            (v \ "graph").as[MutableGraph](MutableGraph.read()),
            (v \ "data").as[Map[String, JsValue]]
          )(sendMessage)
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