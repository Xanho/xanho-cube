package org.xanho.cube.akka.rpc

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef}
import org.xanho.utility.data.DocumentStorage
import org.xanho.web.rpc.FailedRPCResultException
import org.xanho.web.rpc.Messages.{FailedRPCResult, RPC, RPCMessage, RPCResult}
import org.xanho.web.rpc.Protocol.Register.UserAlreadyExistsException
import org.xanho.web.rpc.Protocol.{Heartbeat, Register}
import play.api.libs.json._

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class WebSocketActor(webActor: ActorRef)
  extends Actor with ActorLogging {

  import context.dispatcher

  private val rpcs =
    mutable.Map.empty[String, Promise[RPCResult]]

  private var client: Option[ActorRef] =
    None

  import scala.concurrent.duration._

  context.system.scheduler.schedule(10.seconds, 10.seconds) {
    val id =
      UUID.randomUUID().toString
    sendToClient(Heartbeat(id)).get.future.onComplete {
      case Success(_) =>
      case o =>
        log.error(o.toString)
    }
  }

  def receive: Receive = {
    case WebSocketActor.Messages.Connected(outgoing) =>
      client = Some(outgoing)
    case WebSocketActor.Messages.Receive(message) =>
      receiveFromClient(message)
    case WebSocketActor.Messages.Send(message) =>
      sendToClient(message)
  }

  private def receiveFromClient(message: RPCMessage): Unit =
    message match {
      case call: RPC =>
        WebSocketActor.receiveRPC(call)
          .foreach(
            _.onComplete {
              case Success(result) =>
                self ! WebSocketActor.Messages.Send(result)
              case Failure(e) =>
                self ! WebSocketActor.Messages.Send(
                  FailedRPCResult(call.id, JsString(e.getMessage))
                )
            }
          )
      case result: RPCResult =>
        rpcs.remove(result.id)
          .foreach(_ success result)
      case failedResult: FailedRPCResult =>
        rpcs.remove(failedResult.id)
          .foreach(_ failure FailedRPCResultException(failedResult))
    }

  private def sendToClient(message: RPCMessage): Option[Promise[RPCResult]] = {
    val maybePromise =
      message match {
        case r: RPC if r.responseExpected =>
          val promise =
            Promise[RPCResult]()
          val rId =
            r.id
          rpcs.update(rId, promise)
          import scala.concurrent.duration._
          context.system.scheduler.scheduleOnce(10.seconds)(
            rpcs.get(rId)
              .foreach(_ =>
                self ! WebSocketActor.Messages.Receive(
                  FailedRPCResult(rId, JsString("Timed out"))
                )
              )

          )
          Some(promise)
        case _ =>
          None
      }
    client.get ! message
    maybePromise
  }

}

object WebSocketActor {

  import upickle.Js

  def receiveRPC: PartialFunction[RPC, Option[Future[RPCResult]]] = {
    case Heartbeat(id) =>
      Some(
        Future.successful(
          RPCResult(id, Js.Obj())
        )
      )
    case Register(id, firebaseUser, authToken) =>
      // TODO: Validate Token
      Some(
        DocumentStorage().get("users", firebaseUser.uid)
        .map {
          case Some(v) =>
            Future.failed(UserAlreadyExistsException)
          case None =>
            DocumentStorage().write("users")
        }
      )
  }

  object Messages {

    case class Connected(outgoing: ActorRef)

    sealed trait RPCInstruction

    // Server -> Client
    case class Send(message: RPCMessage)

    // Client -> Server
    case class Receive(message: RPCMessage)

  }

}