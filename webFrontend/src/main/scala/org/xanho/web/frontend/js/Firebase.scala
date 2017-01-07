package org.xanho.web.frontend.js

import org.xanho.web.shared.models

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSName, ScalaJSDefined}

@js.native
object ImportedJS extends js.GlobalScope {
  val firebase: FirebaseStatic.type = js.native
}

@js.native
object FirebaseStatic extends js.Object {
  def initializeApp(config: FirebaseConfig): Unit = js.native

  def auth(): Auth = js.native

  def database(): Database = js.native
}

@ScalaJSDefined
class FirebaseConfig(val apiKey: String,
                     val authDomain: String,
                     val databaseURL: String,
                     val storageBucket: String,
                     val messageSenderId: String) extends js.Object

@js.native
trait Auth extends js.Object {
  var currentUser: js.UndefOr[User] = js.native

  def onAuthStateChanged(nextOrObserver: js.Function1[js.UndefOr[User], Unit]): Unit = js.native

  def signInWithEmailAndPassword(email: String, password: String): js.Promise[User] = js.native

  def signInWithPopup(authProvider: AuthProvider): js.Promise[UserCredential]
}

@js.native
trait User extends js.Object {
  var displayName: js.UndefOr[String] = js.native
  var email: js.UndefOr[String] = js.native
  var emailVerified: Boolean = js.native
  var isAnonymous: Boolean = js.native
  var photoUrl: js.UndefOr[String] = js.native
  var providerData: Seq[UserInfo] = js.native
  var providerId: js.UndefOr[String] = js.native
  var refreshToken: String = js.native
  var uid: String = js.native

  def getToken(): String = js.native

  def getToken(forceRefresh: Boolean): String = js.native
}

object User {
  def toModel(user: User): Option[models.FirebaseUser] =
    user.email.toOption
      .map(
        models.FirebaseUser(
          user.uid,
          _,
          user.emailVerified,
          user.providerId.toOption,
          user.photoUrl.toOption
        )
      )
}

@js.native
trait UserCredential extends js.Object {
  var user: js.UndefOr[User] = js.native
  var credential: js.UndefOr[AuthCredential] = js.native
}

@js.native
trait UserInfo extends js.Object {
  val displayName: String = js.native
  val email: String = js.native
  val photoUrl: String = js.native
  val providerId: String = js.native
  val uid: String = js.native
}

@JSName("firebase.auth.AuthProvider")
@js.native
trait AuthProvider extends js.Object

@js.native
class `firebase.auth.GoogleAuthProvider`() extends js.Object with AuthProvider {
  val providerId: String = js.native

  def credential(idToken: js.UndefOr[String], accessToken: js.UndefOr[String]): AuthCredential = js.native
}

@js.native
trait AuthCredential extends js.Object {
  var provider: String
}

@js.native
trait Database extends js.Object {
  def ref(path: js.UndefOr[String]): Reference = js.native
}

@js.native
trait Reference extends js.Object {
  var key: String = js.native
  var parent: js.UndefOr[Reference] = js.native
  var root: Reference = js.native

  def child(path: String): Reference = js.native

  def set(value: js.Any, onComplete: js.UndefOr[js.Function1[js.UndefOr[js.Error], Unit]]): js.Promise[_] = js.native

  def update(value: js.Any, onComplete: js.UndefOr[js.Function1[js.UndefOr[js.Error], Unit]]): js.Promise[_] = js.native

  def push(value: js.Any, onComplete: js.UndefOr[js.Function1[js.UndefOr[js.Error], Unit]]): js.Thenable[Reference] = js.native

  def remove(onComplete: js.UndefOr[js.Function1[js.UndefOr[js.Error], Unit]]): js.Promise[_] = js.native

  def once(eventType: String,
           successCallback: js.UndefOr[js.Function1[DataSnapshot, Unit]],
           failureCallbackOrContext: js.UndefOr[js.Function1[js.Error, Unit]],
           context: js.UndefOr[js.Any]): Unit = js.native

}

@js.native
trait DataSnapshot extends js.Object {
  @JSName("val")
  def value(): js.Dynamic = js.native

}