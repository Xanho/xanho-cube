package org.xanho.web.frontend.views.components

import org.scalajs.dom.raw.Element
import org.xanho.web.frontend.styles.GlobalStyles
import org.xanho.web.frontend.styles.partials.FooterStyles

import scalacss.ScalatagsCss._
import scalatags.JsDom.all._

object Footer {
  private lazy val template = footer(FooterStyles.footer)(
    div(GlobalStyles.body)(
      div(FooterStyles.footerInner)(
        p(FooterStyles.footerCopyrights)("Copyright © 2016. Xanho.")
      )
    )
  ).render

  def getTemplate: Element = template
}