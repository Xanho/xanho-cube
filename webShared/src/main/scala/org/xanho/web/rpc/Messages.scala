package org.xanho.web.rpc

import upickle.Js
import upickle.default._

object Messages {

  trait RPCMessage


  abstract class RPC extends RPCMessage {
    val id: String
    val methodName: String
    def authToken: Option[String]
    def arguments: Map[String, Js.Value]
    val responseExpected: Boolean
  }

  case class RPCResult(id: String,
                       value: Js.Value) extends RPCMessage

  case class FailedRPCResult(id: String,
                             reason: Js.Value) extends RPCMessage

  object RPCMessage {
    def read(v: Js.Obj): RPCMessage = {
      val obj =
        v.obj
      if (obj contains "methodName")
        readJs(v)(rpcReader)
      else if (obj contains "value")
        readJs(v)(rpcResultReader)
      else
        readJs(v)(failedRPCResultReader)
    }
  }

  implicit val rpcReader: Reader[RPC] =
    Reader[RPC] {
      case v: Js.Obj =>
        v("methodName").str match {
          case "heartbeat" =>
            readJs(v)(Protocol.Heartbeat.read)
          case "register" =>
            readJs(v)(Protocol.Register.read)
        }
    }

  implicit val rpcWriter: Writer[RPC] =
    Writer[RPC](r =>
      Js.Obj(
        Seq(
          "id" -> Js.Str(r.id),
          "methodName" -> Js.Str(r.methodName),
          "arguments" -> Js.Obj(r.arguments.toSeq: _*),
          "responseExpected" -> (if (r.responseExpected) Js.True else Js.False)
        ) ++
          r.authToken.toVector.map(token => "authToken" -> Js.Str(token)): _*
      )
    )

  implicit val rpcResultReader: Reader[RPCResult] =
    Reader[RPCResult] {
      case v: Js.Obj =>
        val obj =
          v.obj
        RPCResult(obj("id").str, obj("value"))
    }

  implicit val rpcResultWriter: Writer[RPCResult] =
    Writer[RPCResult](r =>
      Js.Obj(
        "id" -> Js.Str(r.id),
        "value" -> r.value
      )
    )

  implicit val failedRPCResultReader: Reader[FailedRPCResult] =
    Reader[FailedRPCResult] {
      case v: Js.Obj =>
        val obj =
          v.obj
        FailedRPCResult(obj("id").str, obj("reason"))
    }

  implicit val failedRPCResultWriter: Writer[FailedRPCResult] =
    Writer[FailedRPCResult](r =>
      Js.Obj(
        "id" -> Js.Str(r.id),
        "reason" -> r.reason
      )
    )

  implicit val writeRPCMessage: Writer[RPCMessage] =
    Writer[RPCMessage] {
      case r: RPC =>
        writeJs(r)(rpcWriter)
      case r: RPCResult =>
        writeJs(r)(rpcResultWriter)
      case r: FailedRPCResult =>
        writeJs(r)(failedRPCResultWriter)
    }
}

case class FailedRPCResultException(failedRPCResult: Messages.FailedRPCResult) extends Exception