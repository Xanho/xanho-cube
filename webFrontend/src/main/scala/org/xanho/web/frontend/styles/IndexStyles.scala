package org.xanho.web.frontend.styles

import scala.language.postfixOps
import scalacss.Defaults._

object IndexStyles extends StyleSheet.Inline {

  import dsl._

  import scala.language.postfixOps
  import scalacss.Defaults._

  val banner =
    style(
      backgroundColor(c"#55af51"),
      color.white,
      textAlign.center,
      width(100 %%),
      padding(50 px),
      unsafeChild("h1")(
        fontSize(200 %%),
        paddingTop(2 rem),
        paddingBottom(1 rem)
      ),
      unsafeChild("h2")(
        fontSize(150 %%)
      )
    )
}