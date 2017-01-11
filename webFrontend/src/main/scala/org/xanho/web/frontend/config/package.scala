package org.xanho.web.frontend

import com.typesafe.config.{Config, ConfigFactory}

package object config {

  val Config: Config =
    ConfigFactory.load()

}
