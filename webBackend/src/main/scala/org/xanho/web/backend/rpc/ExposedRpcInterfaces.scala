package org.xanho.web.backend.rpc

import io.udash.rpc._
import org.xanho.frontend.rpc.MainServerRPC

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ExposedRpcInterfaces(implicit clientId: ClientId) extends MainServerRPC {
  override def hello(name: String): Future[String] =
    Future.successful(s"Hello, $name!")

  override def pushMe(): Unit =
    ClientRPC(clientId).push(42)
}

       