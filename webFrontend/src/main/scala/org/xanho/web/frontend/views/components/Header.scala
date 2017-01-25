package org.xanho.web.frontend.views.components

import org.scalajs.dom.raw.Element
import org.xanho.web.frontend.styles.partials.HeaderStyles
import org.xanho.web.frontend.views.IndexState

import scalacss.ScalatagsCss._
import scalatags.JsDom.all._

object Header {

  import org.xanho.web.frontend.Context._

  private def template =
    header(HeaderStyles.header)(
      ul(HeaderStyles.headerList)(
        li(HeaderStyles.headerItem)(
          a(href := IndexState.url)("Xanho")
        )
      )
    ).render

  def getTemplate: Element = template
}