package org.xanho.web.frontend.utility

import org.xanho.web.frontend.js.ImportedJS.firebase
import org.xanho.web.frontend.js.`firebase.auth.GoogleAuthProvider`
import org.xanho.web.frontend.{js => jsLib}
import org.xanho.web.shared.models.FirebaseUser

import scala.concurrent.{Future, Promise}
import scala.scalajs.js

object Auth {

  import org.xanho.web.frontend.Context._

  def observeAuth(callback: Option[FirebaseUser] => Unit): Unit =
    firebase.auth().onAuthStateChanged(
      (maybeUser: js.UndefOr[jsLib.User]) =>
        callback(
          maybeUser.toOption
            .flatMap(Option.apply)
            .flatMap(jsLib.User.toModel)
        )
    )

  def currentFirebaseUser: Option[FirebaseUser] =
    firebase.auth().currentUser
      .toOption
      .flatMap(Option.apply)
      .flatMap(jsLib.User.toModel)

  def logout(): Future[_] =
    firebase.auth().signOut()
      .toFuture

  def loginWithEmailPassword(email: String, password: String): Future[FirebaseUser] =
    firebase.auth().signInWithEmailAndPassword(email, password).toFuture
      .map(org.xanho.web.frontend.js.User.toModel(_).get)

  def loginWithGoogle: Future[FirebaseUser] = {
    val provider =
      new `firebase.auth.GoogleAuthProvider`()
    firebase.auth().signInWithPopup(provider)
      .toFuture
      .map(_.user.toOption)
      .map(_.flatMap(org.xanho.web.frontend.js.User.toModel).get)
  }

  implicit class FirebaseUserHelper(firebaseUser: FirebaseUser) {

    import org.xanho.web.shared.models
    import upickle.default.readJs

    def toUser: Future[models.User] = {
      val promise =
        Promise[models.User]()
      val listenerId =
        Database.listen("users", firebaseUser.uid)(
          Database.Listeners.value(
            promise success readJs(_)(models.User.reader(firebaseUser.uid, firebaseUser.email))
          )
        )
      promise.future
        .andThen { case _ => Database.unlisten(listenerId) }
    }
  }

}