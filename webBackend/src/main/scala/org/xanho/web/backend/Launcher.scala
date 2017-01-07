package org.xanho.web.backend

import org.xanho.web.backend.jetty.JettyServer

object Launcher extends App {
  val server =
    new JettyServer(8080, "webBackend/target/UdashStatic/WebContent")
  server.start()
}