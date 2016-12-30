package org.xanho.cube.akka

import akka.actor.Actor
import org.xanho.cube.core.Message

class MessageHandler extends Actor {

  def receive = {
    case message@Message(_, _, _, destination, _) =>
      if(isUser(destination))
        sendToUser(message)
      else
        sendToCube(message)
  }

  private def sendToUser(message: Message): Unit =
    ???

  private def sendToCube(message: Message): Unit =
    context.actorSelection("/")

  private def isUser(id: String): Boolean =
    ???

}

object MessageHandler {
  object Messages {
  }
}