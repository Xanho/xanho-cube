package org.xanho.cube
import scala.concurrent.duration._
import org.xanho.utility.Config.config
package object akka {

  object Messages {
    case object Ok
    case object NotOk
    case class NotFound(t: Any)
    case object Status
  }

  implicit val defaultTimeout: FiniteDuration =
    10.seconds

  import _root_.akka.util.Timeout

  implicit val defaultAskTimeout: Timeout =
    Timeout(defaultTimeout)

  val actorSystemName: String =
    config.getString("xanho.akka.system.name")

  val cubeMasterName: String =
    config.getString("xanho.akka.cube_master.name")

  val cubeClusterPrefix: String =
    config.getString("xanho.akka.cube_cluster.prefix")

  val apiPrefix: String =
    config.getString("xanho.akka.api.prefix")

  val actorHostname: String =
    config.getString("akka.remote.netty.tcp.hostname")

  val actorPort: Int =
    config.getInt("akka.remote.netty.tcp.port")

  val masterPath: String =
    toRemotePath(
      s"/user/${config.getString("xanho.akka.cube_master.name")}",
      config.getString("xanho.akka.cube_master.hostname"),
      config.getInt("xanho.akka.cube_master.port")
    )

  def clusterPath(name: String): String =
    toRemotePath(
      s"/user/$cubeClusterPrefix$name",
      config.getString("xanho.akka.cube_cluster.hostname"),
      config.getInt("xanho.akka.cube_cluster.port")
    )

  def apiPath(id: String): String =
    toRemotePath(
      s"/user/$apiPrefix$id",
      config.getString("xanho.akka.api.hostname"),
      config.getInt("xanho.akka.api.port")
    )

  def toRemotePath(base: String,
                   host: String,
                   port: Int) =
    s"akka.tcp://$actorSystemName@$host:$port$base"

}
