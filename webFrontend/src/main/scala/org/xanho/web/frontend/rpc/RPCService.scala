package org.xanho.web.frontend.rpc

import org.xanho.frontend.rpc.MainClientRPC

class RPCService extends MainClientRPC {
  override def push(number: Int): Unit =
    println(s"Push from server: $number")
}

       