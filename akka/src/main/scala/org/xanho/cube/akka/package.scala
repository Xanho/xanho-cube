package org.xanho.cube

package object akka {

  object Messages {
    case object Ok
    case object NotOk
    case class NotFound(t: Any)
    case object Terminate
    case object TerminateGracefully
    case object Status
  }

}
