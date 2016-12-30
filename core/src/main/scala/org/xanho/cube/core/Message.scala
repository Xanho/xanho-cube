package org.xanho.cube.core

import play.api.libs.json.{Json, OFormat}


case class Message(id: String,
                   text: String,
                   sourceId: String,
                   destinationId: String,
                   timestamp: Long)

object Message {
  implicit val format: OFormat[Message] =
    Json.format[Message]
}