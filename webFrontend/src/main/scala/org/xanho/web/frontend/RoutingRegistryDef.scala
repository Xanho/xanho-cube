package org.xanho.web.frontend

import io.udash._
import io.udash.utils.Bidirectional
import org.xanho.web.frontend.views.{ChatState, IndexState}

class RoutingRegistryDef extends RoutingRegistry[RoutingState] {
  def matchUrl(url: Url): RoutingState =
    staticUrl2State
      .orElse(appUrl2State)
      .applyOrElse(url.value.stripSuffix("/"), (x: String) => ErrorState)

  def matchState(state: RoutingState): Url =
    Url(
      staticState2Url
        .orElse(appState2Url)
        .apply(state)
    )

  private val (staticUrl2State, staticState2Url) =
    Bidirectional[String, RoutingState] {
      case "" =>
        IndexState
    }

  private val (appUrl2State, appState2Url) =
    Bidirectional[String, RoutingState] {
      case "/chat" =>
        ChatState
    }

}