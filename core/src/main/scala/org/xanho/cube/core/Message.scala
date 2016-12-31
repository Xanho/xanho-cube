package org.xanho.cube.core

import play.api.libs.json.{Json, OFormat}


case class Message(text: String,
                   sourceId: String,
                   destinationId: String,
                   timestamp: Long)

object Message {
  implicit val format: OFormat[Message] =
    Json.format[Message]
}