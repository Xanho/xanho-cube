package org.xanho.cube.akka.rpc

import akka.actor.{Actor, ActorLogging, ActorRef}
import org.xanho.cube.akka._
import org.xanho.utility.FutureUtility.FutureHelper
import org.xanho.web.rpc.FailedRPCResultException
import org.xanho.web.rpc.Messages.{FailedRPCResult, RPC, RPCMessage, RPCResult}
import org.xanho.web.rpc.Protocol.{Heartbeat, Register}
import play.api.libs.json._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class WebSocketActor(webActor: ActorRef)
  extends Actor with ActorLogging {

  import context.dispatcher

  private val rpcs =
    mutable.Map.empty[String, Promise[RPCResult]]

  private val apiActor =
    ApiRouter.ref.await(10.seconds)

  private var client: Option[ActorRef] =
    None

  import upickle.Js

  private def receiveRPC: PartialFunction[RPCMessage, Option[Future[RPCResult]]] = {

    case Heartbeat(id) =>
      Some(
        Future.successful(
          RPCResult(id, Js.Obj())
        )
      )

    case Register(id, firebaseUser, authToken) =>
      // TODO: Validate Token
      import akka.pattern.ask
      import org.xanho.cube.akka.defaultAskTimeout
      Some(
        apiActor
          .ask(ApiActor.Messages.Register(firebaseUser.uid))
          .map {
            case Messages.Ok =>
              RPCResult(id, Js.Str(firebaseUser.uid))
            case _ =>
              throw FailedRPCResultException(
                FailedRPCResult(id, Js.Str(firebaseUser.uid))
              )
          }
      )
  }

  override def postStop(): Unit = {
    rpcs
      .foreach {
        case (key, promise) =>
          promise.failure(
            new IllegalStateException("Web Socket shutting down")
          )
          rpcs.remove(key)
      }
  }

  def receive: Receive = {
    case WebSocketActor.Messages.Connected(outgoing) =>
      client = Some(outgoing)
    case WebSocketActor.Messages.Receive(message) =>
      receiveFromClient(message)
    case WebSocketActor.Messages.Send(message) =>
      sendToClient(message)
    case o =>
      log.warning(s"Unexpected message received: ${o.toString}")
  }

  private def receiveFromClient(message: RPCMessage): Unit =
    message match {
      case call: RPC =>
        receiveRPC(call)
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
        rpcs.get(result.id)
          .foreach(_ success result)
      case failedResult: FailedRPCResult =>
        rpcs.get(failedResult.id)
          .foreach(_ failure FailedRPCResultException(failedResult))
    }

  private def sendToClient(message: RPCMessage): Option[Promise[RPCResult]] = {
    val maybePromise =
      message match {
        case r: RPC if r.responseExpected =>
          val promise =
            Promise[RPCResult]()
          rpcs.update(r.id, promise)
          Some(promise)
        case _ =>
          None
      }
    client.get ! message
    maybePromise
  }

}

object WebSocketActor {

  object Messages {

    case class Connected(outgoing: ActorRef)

    sealed trait RPCInstruction

    // Server -> Client
    case class Send(message: RPCMessage)

    // Client -> Server
    case class Receive(message: RPCMessage)

  }

}