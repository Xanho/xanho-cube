package org.xanho.web.frontend.rpc

import com.avsystem.commons.rpc.RPC
import org.xanho.web.shared.models.{FirebaseUser, User}

import scala.concurrent.Future

@RPC
trait MainServerRPC {
  def register(firebaseUser: FirebaseUser): Future[User]
  def hello(name: String): Future[String]
  def pushMe(): Unit
}