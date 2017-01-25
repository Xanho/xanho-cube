package org.xanho.web.frontend.styles.constants

import scalacss.Defaults._

object StyleConstants extends StyleSheet.Inline{
  import dsl._

  /**
    * SIZES
    */
  object Sizes {
    val BodyWidth = 1075

    val MinSiteHeight = 550

    val HeaderHeight = 48

    val HeaderHeightMobile = 80

    val FooterHeight = 48

    val MobileMenuButton = 50
  }

  /**
    * COLORS
    */
  object Colors {
    val c1 =
      c"#354242"
    val c2 =
      c"#ACEBAE"
    val c3 =
      c"#FFFF9D"
    val c4 =
      c"#C9DE55"
    val c5 =
      c"#7D9100"
  }

  /**
    * MEDIA QUERIES
    */
  object MediaQueriesBounds {
    val TabletLandscapeMax = Sizes.BodyWidth - 1

    val TabletLandscapeMin = 768

    val TabletMax = TabletLandscapeMin - 1

    val TabletMin = 481

    val PhoneMax = TabletMin - 1
  }
}