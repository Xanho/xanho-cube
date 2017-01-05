package org.xanho.web.backend

import org.xanho.web.backend.jetty.ApplicationServer

object Launcher {
  def main(args: Array[String]): Unit = {
    val server = new ApplicationServer(8080, "webBackend/target/UdashStatic/WebContent")
    server.start()
  }
}