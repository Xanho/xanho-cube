package org.xanho.web.frontend

import io.udash.{Application, State}

abstract class RoutingState(val parentState: RoutingState) extends State {
  def url(implicit application: Application[RoutingState]): String =
    s"#${application.matchState(this).value}"
}

case object ErrorState extends RoutingState(null)

