package org.xanho.web.frontend.rpc

import java.util.UUID

import org.scalajs.dom.raw.WebSocket
import org.scalajs.dom.{ErrorEvent, Event, MessageEvent}
import org.xanho.web.frontend.config.Config
import org.xanho.web.rpc.Protocol.Heartbeat
import org.xanho.web.rpc.{FailedRPCResultException, Messages, Protocol}
import org.xanho.web.shared.models.FirebaseUser
import upickle.Js
import upickle.default.{read, write}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

object RPC {

  private val wsAddress =
    Config.getString("xanho.ws.address")

  import org.xanho.web.frontend.Context.executionContext

  private val rpcs =
    mutable.Map.empty[String, Promise[Messages.RPCResult]]

  private val connection =
    Promise[Boolean]()

  private val socket =
    new WebSocket(s"$wsAddress")

  socket.onopen = {
    (_: Event) =>
      println(s"WS Connection Made")
      connection success true
  }

  socket.onerror =
    (event: ErrorEvent) =>
      println(s"WS Connection Failed: $event")

  socket.onmessage =
    (event: MessageEvent) =>
      receiveFromServer(
        Messages.RPCMessage.read(read[Js.Obj](event.data.toString))
      )

  private val outboundQueue =
    mutable.Queue.empty[String]

  private val handleCall: PartialFunction[Messages.RPC, Option[Future[Messages.RPCResult]]] = {
    case Heartbeat(id) =>
      Some(
        Future.successful(
          Messages.RPCResult(id, Js.Obj())
        )
      )
  }

  private def receiveFromServer(message: Messages.RPCMessage): Unit =
    message match {
      case call: Messages.RPC =>
        handleCall(call)
          .foreach(
            _.onComplete {
              case Success(result) =>
                sendToServer(result)
              case Failure(e) =>
                sendToServer(
                  Messages.FailedRPCResult(call.id, Js.Str(e.getMessage))
                )
            }
          )
      case result: Messages.RPCResult =>
        println(s"Received result $result")
        rpcs.get(result.id)
          .foreach(_ success result)
      case failedResult: Messages.FailedRPCResult =>
        println(s"Received failure $failedResult")
        rpcs.get(failedResult.id)
          .foreach(_ failure FailedRPCResultException(failedResult))
    }

  private def sendToServer(message: Messages.RPCMessage): Option[Promise[Messages.RPCResult]] = {
    println(message)
    val maybePromise =
      message match {
        case r: Messages.RPC if r.responseExpected =>
          val promise =
            Promise[Messages.RPCResult]()
          rpcs.update(r.id, promise)
          import scala.scalajs.js.timers._
          setTimeout(10000)(
            rpcs.get(r.id)
              .foreach(_ =>
                receiveFromServer(
                  Messages.FailedRPCResult(r.id, Js.Str("Timed out"))
                )
              )
          )
          outboundQueue.enqueue(write(r)(Messages.rpcWriter))
          Some(promise)
        case r: Messages.RPC =>
          outboundQueue.enqueue(write(r)(Messages.rpcWriter))
          None
        case r: Messages.RPCResult =>
          outboundQueue.enqueue(write(r))
          None
        case r: Messages.FailedRPCResult =>
          outboundQueue.enqueue(write(r))
          None
      }
    flushQueue()
    maybePromise
  }

  def register(firebaseUser: FirebaseUser): Future[_] = {
    val id =
      UUID.randomUUID().toString
    sendToServer(
      Protocol.Register(
        id,
        firebaseUser,
        None
      )
    ).get
      .future
      .andThen {
        case x =>
          println(x)
          rpcs.remove(id)
      }
  }

  def heartbeat: Future[_] = {
    val id =
      UUID.randomUUID().toString
    sendToServer(Protocol.Heartbeat(id))
    val f = rpcs(id).future
    f onComplete (_ => rpcs.remove(id))
    f
  }

  import scala.scalajs.js.timers._

  def flushQueue(): Unit =
    if (socket.readyState == 1)
      while (outboundQueue.nonEmpty) {
        val item =
          outboundQueue.dequeue()
        println(s"Sending: $item")
        socket.send(item)
      }
    else
      setTimeout(1000)(flushQueue())

}