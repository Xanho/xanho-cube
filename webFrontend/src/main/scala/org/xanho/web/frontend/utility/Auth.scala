package org.xanho.web.frontend.utility

import org.xanho.web.frontend.js.ImportedJS.firebase
import org.xanho.web.frontend.{js => jsLib}
import org.xanho.web.shared.models.FirebaseUser

import scala.scalajs.js

object Auth {

  def observeUser(callback: Option[FirebaseUser] => Unit): Unit =
    firebase.auth().onAuthStateChanged(
      (maybeUser: js.UndefOr[jsLib.User]) =>
        callback(
          maybeUser.toOption
            .flatMap(Option.apply)
            .flatMap(jsLib.User.toModel)
        )
    )

  def currentUser: Option[FirebaseUser] =
    firebase.auth().currentUser
      .toOption
      .flatMap(Option.apply)
      .flatMap(jsLib.User.toModel)

}