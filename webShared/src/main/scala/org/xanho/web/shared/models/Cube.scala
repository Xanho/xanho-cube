package org.xanho.web.shared.models

import java.util.Date

import upickle.Js
import upickle.default._

case class Cube(id: String,
                ownerId: String,
                creationDate: Date,
                messages: Seq[Message])

object Cube {
  def reader(id: String): Reader[Cube] =
    Reader[Cube] {
      case o: Js.Obj =>
        val obj = o.obj
        Cube(
          id,
          obj("ownerId").str,
          new Date(obj("creationDate").num.toLong),
          o.obj.get("messages")
            .fold(Seq.empty[Message])(_.arr.map(readJs[Message]))
        )
    }

  implicit val writer: Writer[Cube] =
    Writer[Cube](cube =>
      Js.Obj(
        "ownerId" -> Js.Str(cube.ownerId),
        "creationDate" -> Js.Num(cube.creationDate.getTime),
        "messages" -> Js.Arr(cube.messages.map(writeJs[Message]): _*)
      )
    )
}
