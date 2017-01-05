package org.xanho.web.backend.rpc

import io.udash.rpc._
import org.xanho.frontend.rpc.MainClientRPC

import scala.concurrent.ExecutionContext

object ClientRPC {
  def apply(target: ClientRPCTarget)(implicit ec: ExecutionContext): MainClientRPC = {
    new DefaultClientRPC[MainClientRPC](target).get
  }
}

       