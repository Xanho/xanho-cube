package org.xanho.web.backend

import org.xanho.cube.akka.WebActor

object Launcher extends App {

  WebActor.initialize(
    host = "127.0.0.1",
    port = 8080,
    resourceBasePath = "./webBackend/target/UdashStatic/WebContent/"
  )

}