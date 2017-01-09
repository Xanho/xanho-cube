package org.xanho.web.rpc

import org.xanho.web.shared.models.FirebaseUser
import upickle.Js
import upickle.Js.Value
import upickle.default._

object Protocol {

  case class Heartbeat(id: String) extends Messages.RPC {
    val methodName: String = "heartbeat"

    def authToken: Option[String] = None

    def arguments: Map[String, Value] = Map.empty

    val responseExpected: Boolean = true
  }

  object Heartbeat {
    implicit val read: Reader[Heartbeat] =
      Reader[Heartbeat] {
        case o: Js.Obj =>
          Heartbeat(o("id").str)
      }
  }

  case class Register(id: String,
                      firebaseUser: FirebaseUser,
                      authToken: Option[String]) extends Messages.RPC {
    val arguments: Map[String, Js.Value] =
      Map("firebaseUser" -> writeJs(firebaseUser))
    val methodName: String =
      "register"
    val responseExpected: Boolean =
      true
  }

  object Register {
    implicit val read: Reader[Register] =
      Reader[Register] {
        case o: Js.Obj =>
          Register(
            o("id").str,
            readJs[FirebaseUser](o("arguments").obj("firebaseUser")),
            o.obj.get("authToken").map(_.str)
          )
      }

    sealed abstract class RegisterException extends Exception
    case object UserAlreadyExistsException extends RegisterException
  }

}
