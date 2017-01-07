package org.xanho.web.frontend.rpc

class RPCService extends MainClientRPC {
  def push(id: Int): Unit =
    println(id)
}