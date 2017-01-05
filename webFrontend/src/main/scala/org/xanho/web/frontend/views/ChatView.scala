package org.xanho.web.frontend.views

import io.udash.properties.model.ModelProperty
import io.udash.properties.{Invalid, Valid}
import io.udash.{FinalView, Presenter, ViewPresenter}
import org.scalajs.dom.html.Div
import org.xanho.web.frontend.Context.applicationInstance
import org.xanho.web.frontend.RoutingState
import org.xanho.web.frontend.utility.Auth
import org.xanho.web.shared.models.FirebaseUser

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
                li(bind(message.asModel.subProp(_.sourceId))),
                li(bind(message.asModel.subProp(_.destinationId))),
                li(bind(message.asModel.subProp(_.text))),
                li(bind(message.asModel.subProp(_.timestamp)))
              )
            ).render
          )
        )
      ),
      TextInput.debounced(model.subProp(_.currentUserText))(),
      button()("Send")
    )

}

class ChatPresenter(model: ModelProperty[ChatViewModel]) extends Presenter[ChatState.type] {

  def handleState(state: ChatState.type): Unit = {}

  Auth.observeUser {
    case m: Some[FirebaseUser] =>
      model.subProp(_.user).set(m)
    case _ =>
      applicationInstance.goTo(LoginState)
  }
}

object ChatViewPresenter extends ViewPresenter[ChatState.type] {

  import org.xanho.web.frontend.Context._

  override def create(): (ChatView, ChatPresenter) = {
    val model =
      ModelProperty(
        ChatViewModel(Auth.currentUser, "", "", Seq.empty)
      )

    model.subProp(_.currentUserText)
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

case class ChatViewModel(user: Option[FirebaseUser],
                         cubeId: String,
                         currentUserText: String,
                         messages: Seq[Message])

case class Message(text: String,
                   sourceId: String,
                   destinationId: String,
                   timestamp: Long)