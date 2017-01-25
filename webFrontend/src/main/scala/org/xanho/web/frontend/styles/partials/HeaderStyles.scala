package org.xanho.web.frontend.styles.partials

import org.xanho.web.frontend.styles.constants.StyleConstants

import scala.language.postfixOps
import scalacss.Defaults._

object HeaderStyles extends StyleSheet.Inline {

  import dsl._

  val header = style(
    backgroundColor(c"#3c7739"),
    height(StyleConstants.Sizes.HeaderHeight px)
  )

  val headerList =
    style(
      listStyleType := none,
      margin.horizontal(auto),
      width(StyleConstants.Sizes.BodyWidth px),
      padding.`0`
    )

  val headerItem =
    style(
      float.left,
      unsafeChild("a")(
        display.block,
        color.white,
        textAlign.center,
        padding.horizontal(16 px),
        padding.vertical(14 px),
        &.hover(
          &.not(".active")(
            backgroundColor(c"#111")
          )
        )
      )
    )

  val headerItemRight =
    style(
      float.right
    )
}