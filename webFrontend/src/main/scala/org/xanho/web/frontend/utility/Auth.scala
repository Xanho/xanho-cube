package org.xanho.web.frontend.utility

import java.util.NoSuchElementException

import org.xanho.web.frontend.js.ImportedJS.firebase
import org.xanho.web.frontend.js.`firebase.auth.GoogleAuthProvider`
import org.xanho.web.frontend.rpc.RPC
import org.xanho.web.frontend.{js => jsLib}
import org.xanho.web.shared.models.FirebaseUser

import scala.concurrent.Future
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
    import upickle.Js
    import upickle.default.readJs

    def toUser: Future[models.User] = {
      def fetchUser(tries: Int = 4): Future[Js.Value] =
        if (tries > 0)
          Database.get("users", firebaseUser.uid)
            .recoverWith {
              case _: NoSuchElementException =>
                RPC.register(firebaseUser)
                  .flatMap(_ => fetchUser(tries - 1))
            }
        else
          Future.failed(new NoSuchElementException(firebaseUser.uid))

      fetchUser()
        .map(readJs(_)(models.User.reader(firebaseUser.uid, firebaseUser.email)))
    }
  }

}