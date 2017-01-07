package org.xanho.web.backend.rpc

import io.udash.rpc._
import org.xanho.utility.data.DocumentStorage
import org.xanho.web.frontend.rpc.MainServerRPC
import org.xanho.web.shared.models.{FirebaseUser, User}
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ExposedRpcInterfaces(implicit clientId: ClientId) extends MainServerRPC {
  def register(firebaseUser: FirebaseUser): Future[User] =
    DocumentStorage.default.write("users", firebaseUser.uid)(???)
      .flatMap(_ => DocumentStorage.default.get("users", firebaseUser.uid))
      .map(_.get.as[User](Json.reads[User]))

  override def hello(name: String): Future[String] =
    Future.successful(s"Hello, $name!")

  override def pushMe(): Unit =
    ClientRPC(clientId).push(42)

}

       