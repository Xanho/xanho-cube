package org.xanho.web.frontend

import io.udash.{Application, StrictLogging}
import io.udash.wrappers.jquery._
import org.scalajs.dom.{Element, document}
import org.xanho.web.frontend.js.{FirebaseConfig, ImportedJS}
import org.xanho.web.frontend.rpc.{MainClientRPC, MainServerRPC, RPCService}
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

  import io.udash.rpc._

  val serverRpc: MainServerRPC =
    DefaultServerRPC[MainClientRPC, MainServerRPC](new RPCService)

}

object Init extends JSApp with StrictLogging {

  import Context._

  @JSExport
  override def main(): Unit = {

    jQ(document).ready((_: Element) => {
      val appRoot = jQ("#application").get(0)
      if (appRoot.isEmpty) {
        logger.error("Application root element not found! Check your index.html file!")
      } else {
        ImportedJS.firebase.initializeApp(
          new FirebaseConfig(
            "AIzaSyDZsFvxRELTzcvjkCa_JHNsVu3bJg5B6e8",
            "xanho-151422.firebaseapp.com",
            "https://xanho-151422.firebaseio.com",
            "xanho-151422.appspot.com",
            "943127112483"
          )
        )

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