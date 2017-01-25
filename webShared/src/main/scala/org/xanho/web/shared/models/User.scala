package org.xanho.web.shared.models

import java.util.Date

import upickle.Js

case class User(uid: String,
                email: String,
                firstName: Option[String],
                lastName: Option[String],
                birthDate: Option[Date],
                cubeId: Option[String])

object User {
  def default(uid: String,
              email: String): User =
    User(uid, email, None, None, None, None)

  import upickle.default._

  def reader(uid: String,
             email: String): Reader[User] =
    Reader[User] {
      case o: Js.Obj =>
        val obj = o.obj
        User(
          uid,
          email,
          obj.get("firstName").map(_.str),
          obj.get("lastName").map(_.str),
          obj.get("birthDate").map(_.num.toLong).map(new Date(_)),
          obj.get("cubeId").map(_.str)
        )
    }

  implicit val writer: Writer[User] =
    Writer[User](
      user =>
        Js.Obj(
          user.firstName.toSeq.map("firstName" -> Js.Str(_)) ++
            user.lastName.map("lastName" -> Js.Str(_)) ++
            user.birthDate.map(_.getTime).map("birthDate" -> Js.Num(_)) ++
            user.firstName.map("cubeId" -> Js.Str(_))
            : _*
        )
    )
}