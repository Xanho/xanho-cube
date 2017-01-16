package org.xanho.web.frontend

import io.udash.wrappers.jquery._
import io.udash.{Application, StrictLogging}
import org.scalajs.dom._
import org.xanho.web.frontend.js.{FirebaseConfig, ImportedJS}
import org.xanho.web.frontend.rpc.RPC
import org.xanho.web.frontend.styles.partials.{FooterStyles, HeaderStyles}
import org.xanho.web.frontend.styles.{GlobalStyles, IndexStyles}
import org.xanho.web.frontend.views.IndexState

import scala.concurrent.ExecutionContextExecutor
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport

object Context {
  implicit val executionContext: ExecutionContextExecutor =
    scalajs.concurrent.JSExecutionContext.Implicits.queue

  private val routingRegistry =
    new RoutingRegistryDef

  private val viewPresenterRegistry =
    new StatesToViewPresenterDef

  implicit val applicationInstance =
    new Application[RoutingState](routingRegistry, viewPresenterRegistry, IndexState)

  RPC

}

object Init extends JSApp with StrictLogging {

  import Context._

  @JSExport
  override def main(): Unit = {
    import org.xanho.web.frontend.config.Config
    jQ(document).ready((_: Element) => {
      val appRoot = jQ("#application").get(0)
      if (appRoot.isEmpty) {
        logger.error("Application root element not found! Check your index.html file!")
      } else {
        ImportedJS.firebase.initializeApp(
          new FirebaseConfig(
            Config.getString("firebase.apikKey"),
            Config.getString("firebase.authDomain"),
            Config.getString("firebase.databaseURL"),
            Config.getString("firebase.storageBucket"),
            Config.getString("firebase.messageSenderId")
          )
        )
        logger.info("Firebase initialized")

        logger.info("Initializing context")
        import Context._
        Context

        applicationInstance.run(appRoot.get)

        import scalacss.Defaults._
        import scalacss.ScalatagsCss._
        import scalatags.JsDom._
        jQ(GlobalStyles.render[TypedTag[org.scalajs.dom.raw.HTMLStyleElement]].render).insertBefore(appRoot.get)
        jQ(IndexStyles.render[TypedTag[org.scalajs.dom.raw.HTMLStyleElement]].render).insertBefore(appRoot.get)
        jQ(FooterStyles.render[TypedTag[org.scalajs.dom.raw.HTMLStyleElement]].render).insertBefore(appRoot.get)
        jQ(HeaderStyles.render[TypedTag[org.scalajs.dom.raw.HTMLStyleElement]].render).insertBefore(appRoot.get)
      }
    })
  }
}