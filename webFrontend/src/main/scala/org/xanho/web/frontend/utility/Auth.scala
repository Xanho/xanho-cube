package org.xanho.web.frontend.utility

import java.util.Date

import org.xanho.web.frontend.js.ImportedJS.firebase
import org.xanho.web.frontend.js.`firebase.auth.GoogleAuthProvider`
import org.xanho.web.frontend.{js => jsLib}
import org.xanho.web.shared.models.{FirebaseUser, User}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
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

  implicit def firebaseUserToUser(firebaseUser: FirebaseUser): User =
    Await.result(
      Database.get("users", firebaseUser.uid)
        .flatMap {
          case Some(v) =>
            val obj =
              v.obj
            Future.successful(
              User(
                firebaseUser.uid,
                firebaseUser.email,
                obj.get("firstName").map(_.str),
                obj.get("lastName").map(_.str),
                obj.get("birthDate").map(_.num.toLong).map(new Date(_)),
                obj.get("cubeId").map(_.str)
              )
            )
          case _ =>
            serverRpc.register(firebaseUser)
        },
      5.seconds
    )

}