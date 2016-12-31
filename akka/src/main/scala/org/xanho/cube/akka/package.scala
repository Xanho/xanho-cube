package org.xanho.cube
import scala.concurrent.duration._
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

}
