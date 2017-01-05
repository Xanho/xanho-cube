package org.xanho.web.frontend

import io.udash._
import org.xanho.web.frontend.views._

class StatesToViewPresenterDef extends ViewPresenterRegistry[RoutingState] {
  def matchStateToResolver(state: RoutingState): ViewPresenter[_ <: RoutingState] =
    state match {
      case IndexState =>
        IndexViewPresenter
      case ChatState =>
        ChatViewPresenter
      case LoginState =>
        LoginViewPresenter
      case _ =>
        ErrorViewPresenter
    }
}