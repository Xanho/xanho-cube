package org.xanho.web.frontend.views

import io.udash._
import io.udash.properties.model.ModelProperty
import org.scalajs.dom.Event
import org.xanho.web.frontend.utility.Auth
import org.xanho.web.frontend.{Context, RoutingState}
import org.xanho.web.shared.models.FirebaseUser

import scala.util.{Failure, Success}

class LoginView(model: ModelProperty[LoginViewModel],
                presenter: LoginPresenter) extends FinalView {

  import scalatags.JsDom.all._

  def getTemplate = {
    val loginButton =
      button(onclick :+= ((ev: Event) => presenter.login(), true))("Login")

    val errorsList =
      ul(
        repeat(model.subSeq(_.errors))(error => li(error.get).render)
      )

    div(
      div(
        loginButton.render
      ),
      div(
        h1("Errors:"),
        errorsList.render
      )
    )
  }

}

class LoginPresenter(model: ModelProperty[LoginViewModel]) extends Presenter[LoginState.type] {

  import Context._

  def handleState(state: LoginState.type): Unit = {}

  Auth.observeAuth(model.subProp(_.user).set)

  model.subProp(_.user)
    .listen(maybeUser => if (maybeUser.nonEmpty) Context.applicationInstance.goTo(IndexState))

  def login(): Unit =
    Auth.loginWithGoogle
      .onComplete {
        case Success(user) =>
          Context.applicationInstance.goTo(IndexState)
        case Failure(e) =>
          model.subSeq(_.errors).append(e.toString)
      }

}

object LoginViewPresenter extends ViewPresenter[LoginState.type] {

  import org.xanho.web.frontend.Context._

  override def create() = {
    val model =
      ModelProperty(
        LoginViewModel(Seq.empty, Auth.currentFirebaseUser)
      )

    val presenter =
      new LoginPresenter(model)
    val view =
      new LoginView(model, presenter)
    (view, presenter)
  }

}

case object LoginState extends RoutingState(null)

case class LoginViewModel(errors: Seq[String], user: Option[FirebaseUser])