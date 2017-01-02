package org.xanho.cube.akka.api.models

import java.util.Date

import org.xanho.utility.data.Buckets
import play.api.libs.json.{Json, OFormat}

case class User(firstName: Option[String],
                lastName: Option[String],
                birthDate: Option[Date],
                cubeId: Option[String])

object User extends ModelCompanion {

  val bucket =
    Buckets.USERS

  implicit val format: OFormat[User] =
    Json.format[User]
}
