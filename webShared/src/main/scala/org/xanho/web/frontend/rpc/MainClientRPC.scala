package org.xanho.web.frontend.rpc

import com.avsystem.commons.rpc.RPC

@RPC
trait MainClientRPC {
  def push(id: Int): Unit
}
       