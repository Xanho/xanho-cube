package org.xanho.web.shared.models

import upickle.Js
import upickle.default._

case class Message(text: String,
                   sourceId: String,
                   destinationId: String,
                   timestamp: Long)

object Message {

  implicit val reader: Reader[Message] =
    Reader[Message] {
      case o: Js.Obj =>
        val obj = o.obj
        Message(
          obj("text").str,
          obj("sourceId").str,
          obj("destinationId").str,
          obj("timestamp").num.toLong
        )
    }

  implicit val writer: Writer[Message] =
    Writer[Message](
      message =>
        Js.Obj(
          "text" -> Js.Str(message.text),
          "sourceId" -> Js.Str(message.sourceId),
          "destinationId" -> Js.Str(message.destinationId),
          "timestamp" -> Js.Num(message.timestamp)
        )
    )

}
