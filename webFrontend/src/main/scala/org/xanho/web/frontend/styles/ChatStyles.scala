package org.xanho.web.frontend.styles

import org.xanho.web.frontend.styles.constants.StyleConstants.Colors

import scala.language.postfixOps
import scalacss.Defaults._

object ChatStyles extends StyleSheet.Inline {

  import dsl._

  import scala.language.postfixOps
  import scalacss.Defaults._

  val chatViewContainer =
    style(
      height(100 vh)
    )

  val messagesBox =
    style(
      width(100 %%),
      height(90 %%),
      overflowY.scroll,
      backgroundColor(Colors.c4),
      clear.both
    )

  val message =
    style(
      margin(4 rem),
      padding(2 rem),
      borderRadius(1 rem),
      maxWidth(60 %%),
      clear.both
    )

  val messageLeft =
    style(
      float.left,
      backgroundColor(Colors.c2),
      borderBottomLeftRadius(0 px)
    )

  val messageRight =
    style(
      float.right,
      backgroundColor(Colors.c3),
      borderBottomRightRadius(0 px)
    )

  val messageText =
    style(
      paddingBottom(1 rem)
    )

  val messageTimestamp =
    style(
      fontStyle.italic,
      fontSize(90 %%),
      textAlign.right
    )


  val userInputContainer =
    style(
      width(100 %%),
      height(10 %%),
      backgroundColor(Colors.c5),
      display.table
    )

  val textInput =
    style(
      backgroundColor.transparent,
      height(100 %%),
      border(0 px),
      width.auto
    )

  val submitButton =
    style(
      width(10 rem),
      float.right,
      height(100 %%),
      fontWeight.bold,
      backgroundColor(Colors.c2),
      borderWidth(0 px)
    )
}