package org.xanho.web.frontend.views

import io.udash.properties.model.ModelProperty
import io.udash.properties.{Invalid, Valid}
import io.udash.{FinalView, Presenter, ViewPresenter}
import org.scalajs.dom.Event
import org.scalajs.dom.html.Div
import org.xanho.web.frontend.RoutingState
import org.xanho.web.frontend.utility.{Auth, Database}
import org.xanho.web.shared.models.{Message, User}
import upickle.Js

import scala.util.Success
import scalacss.internal.LengthUnit.px
import scalatags.JsDom.TypedTag

class ChatView(model: ModelProperty[ChatViewModel],
               presenter: ChatPresenter) extends FinalView {

  import io.udash._

  import scalatags.JsDom.all._

  def getTemplate: TypedTag[Div] =
    div(
      p(
        "Hello ",
        span(
          bind(
            model.subProp(_.user)
              .transform(_.fold("Anonymous")(_.email))
          )
        )
      ),
      div(
        ul(
          repeat(
            model.subSeq(_.messages)
          )(message =>
            li(
              ul(
                li(
                  b("Source ID: "),
                  bind(message.asModel.subProp(_.sourceId))
                ),
                li(
                  b("Destination ID: "),
                  bind(message.asModel.subProp(_.destinationId))
                ),
                li(
                  b("Message Text: "),
                  bind(message.asModel.subProp(_.text))
                ),
                li(
                  b("Timestamp: "),
                  bind(message.asModel.subProp(_.timestamp))
                )
              ),
              br
            ).render
          )
        )
      ),
      TextInput.debounced(model.subProp(_.currentUserText))(),
      button(
        onclick :+= ((_: Event) => presenter.sendMessage())
      )("Send")
    )

}

class ChatPresenter(model: ModelProperty[ChatViewModel]) extends Presenter[ChatState.type] {

  def handleState(state: ChatState.type): Unit = {}

  import org.xanho.web.frontend.Context._

  Auth.observeAuth {
    case Some(firebaseUser) =>
      import org.xanho.web.frontend.utility.Auth.FirebaseUserHelper
      firebaseUser.toUser
        .onSuccess {
          case u => model.subProp(_.user).set(Some(u))
        }
    case _ =>
      applicationInstance.goTo(IndexState)
  }

  model.subProp(_.user).listen {
    case Some(User(_, _, _, _, _, Some(cubeId))) =>
      import upickle.default._
      Database.listen("cubes", cubeId, "messages")(
        Database.Listeners.childAdded {
          (v: Js.Value) =>
            model.subSeq(_.messages)
              .append(readJs[Message](v)(Message.reader))
        }
      )
    case _ =>
      model.subSeq(_.messages).clear()
  }

  def sendMessage(): Unit =
    model.subProp(_.currentUserText).isValid onComplete {
      case Success(Valid) =>
        val user =
          model.subProp(_.user).get.get
        val cubeId =
          user.cubeId.get
        val message =
          Message(
            model.subProp(_.currentUserText).get,
            user.uid,
            cubeId,
            System.currentTimeMillis()
          )
        import upickle.default.writeJs
        val json =
          writeJs[Message](message)(Message.writer)
        Database.append("cubes", cubeId, "messages")(json)
    }

}

object ChatViewPresenter extends ViewPresenter[ChatState.type] {

  import org.xanho.web.frontend.Context._

  override def create(): (ChatView, ChatPresenter) = {
    val model =
      ModelProperty(
        ChatViewModel(
          None,
          Seq.empty,
          ""
        )
      )

    model
      .subProp(_.currentUserText)
      .addValidator(text =>
        if (text.nonEmpty)
          Valid
        else
          Invalid("Message can't be empty")
      )

    val presenter =
      new ChatPresenter(model)
    val view =
      new ChatView(model, presenter)
    (view, presenter)
  }

}

case object ChatState extends RoutingState(null)

case class ChatViewModel(user: Option[User],
                         messages: Seq[Message],
                         currentUserText: String)
