package org.xanho.cube.akka
import org.xanho.utility.Config.config
object Application extends App {

  args indexOf "-type" match {
    case -1 =>
      throw new IllegalArgumentException("No Actor type specified")
    case i =>
      args(i + 1) match {
        case "cube-master" =>
          CubeMaster.initialize()

        case "cube-cluster" =>
          CubeCluster.initialize()

        case "api" =>
          val host =
            config.getString("xanho.api.host")
          val port =
            config.getInt("xanho.api.port")
          ApiActor.initialize(host, port)
      }
  }

}
