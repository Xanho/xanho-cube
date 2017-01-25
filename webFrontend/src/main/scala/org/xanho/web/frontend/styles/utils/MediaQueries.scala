package org.xanho.web.frontend.styles.utils

import org.xanho.web.frontend.styles.constants.StyleConstants

import scala.language.postfixOps
import scalacss.Defaults._

object MediaQueries extends StyleSheet.Inline {

  import dsl._

  def tabletLandscape(properties: StyleA) = style(
    media.screen.minWidth(1 px).maxWidth(StyleConstants.MediaQueriesBounds.TabletLandscapeMax px)(
      properties
    )
  )

  def tabletPortrait(properties: StyleA) = style(
    media.screen.minWidth(1 px).maxWidth(StyleConstants.MediaQueriesBounds.TabletMax px)(
      properties
    )
  )

  def phone(properties: StyleA) = style(
    media.screen.minWidth(1 px).maxWidth(StyleConstants.MediaQueriesBounds.PhoneMax px)(
      properties
    )
  )
}