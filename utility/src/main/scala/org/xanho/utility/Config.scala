package org.xanho.utility

import com.typesafe.config.ConfigFactory

object Config {

  implicit val config: com.typesafe.config.Config =
    ConfigFactory.load()

}
