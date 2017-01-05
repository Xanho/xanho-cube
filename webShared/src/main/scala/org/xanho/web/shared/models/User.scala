package org.xanho.web.shared.models

import java.util.Date

case class User(uid: String,
                email: String,
                firstName: Option[String],
                lastName: Option[String],
                birthDate: Option[Date],
                cubeId: Option[String])
