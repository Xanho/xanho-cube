package org.xanho.web.backend.jetty

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.eclipse.jetty.server.session.SessionHandler
import org.eclipse.jetty.servlet.{DefaultServlet, ServletContextHandler, ServletHolder}
import org.xanho.cube.akka.WebActor
import org.xanho.web.backend.rpc.ExposedRpcInterfaces

class JettyServer(val port: Int, resourceBase: String) {
  private val server: Server = new Server(port)
  private val contextHandler = new ServletContextHandler

  contextHandler.setSessionHandler(new SessionHandler)
  contextHandler.setGzipHandler(new GzipHandler)
  server.setHandler(contextHandler)

  @volatile private var stopped =
    false

  private val (actor, system) =
    WebActor.initialize(
      receiveHandler = PartialFunction.empty[Any, Unit],
      shutdownHandler = () => server.stop()
    )

  def start(): Unit =
    server.start()

  def stop(): Unit = if (!stopped) {
    import akka.pattern.gracefulStop
    import org.xanho.utility.FutureUtility.FutureHelper

    import scala.concurrent.duration._
    stopped = true
    gracefulStop(actor, 10.seconds).await(12.seconds)
    server.stop()
  }

  private val appHolder = {
    val holder = new ServletHolder(new DefaultServlet)
    holder.setAsyncSupported(true)
    holder.setInitParameter("resourceBase", resourceBase)
    holder
  }
  contextHandler.addServlet(appHolder, "/*")

  private val atmosphereHolder: ServletHolder = {
    import io.udash.rpc._
    import org.xanho.web.frontend.rpc._

    val config =
      new DefaultAtmosphereServiceConfig[MainServerRPC](
        (clientId) =>
          new DefaultExposesServerRPC[MainServerRPC](
            new ExposedRpcInterfaces()(clientId)
          )
      )

    val framework =
      new DefaultAtmosphereFramework(config)(system.dispatcher)
        .allowAllClassesScan(false)
        .init()

    val holder = new ServletHolder(new RpcServlet(framework))
    holder.setAsyncSupported(true)
    holder
  }
  contextHandler.addServlet(atmosphereHolder, "/atm/*")

}

       