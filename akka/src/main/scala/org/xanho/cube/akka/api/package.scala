package org.xanho.cube.akka

import akka.http.scaladsl.server.directives.Credentials
import com.google.firebase.auth.{FirebaseAuth, FirebaseToken}
import com.google.firebase.tasks.{OnFailureListener, OnSuccessListener}
import org.xanho.utility.Config.config

import scala.concurrent.{ExecutionContext, Future, Promise}

package object api {

  private val secret =
    config.getString("firebase.private_key")

  val realm: String =
    "xanho.org"

  /**
    * Authenticates the given credentials
    *
    * @param credentials The credentials to validate
    * @return An optional tuple containing (Token, User ID, User Email)
    */
  def authenticator(credentials: Credentials)(implicit ec: ExecutionContext): Future[Option[(String, String, String)]] =
    credentials match {
      case Credentials.Provided(token) =>
        val promise = Promise[Option[(String, String, String)]]()
        FirebaseAuth.getInstance().verifyIdToken(token)
          .addOnSuccessListener(new OnSuccessListener[FirebaseToken] {
            def onSuccess(tResult: FirebaseToken): Unit =
              promise success Some((token, tResult.getUid, tResult.getEmail))
          })
          .addOnFailureListener(new OnFailureListener {
            def onFailure(e: Exception): Unit =
              promise failure e
          })

        val future =
          promise.future

        future.onFailure { case _ => Future.successful(None) }
        future
      case _ =>
        Future.successful(None)
    }

}
