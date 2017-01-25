package org.xanho.cube.akka

import play.api.libs.json._
import upickle.Js
import upickle.Js.Value


package object rpc {

  import scala.language.implicitConversions

  implicit def uPickleToPlay(v: Js.Value): JsValue =
    v match {
      case v: Js.Str =>
        JsString(v.value)
      case v: Js.Num =>
        JsNumber(v.value)
      case Js.True =>
        JsBoolean(true)
      case Js.False =>
        JsBoolean(false)
      case v: Js.Obj =>
        JsObject(v.obj.mapValues(uPickleToPlay))
      case v: Js.Arr =>
        JsArray(v.value.map(uPickleToPlay))
      case Js.Null =>
        JsNull
    }

  implicit val formatUPickle: Format[Value] =
    Format[Js.Value](
      Reads[Js.Value](v => JsSuccess(playToUPickle(v))),
      Writes[Js.Value](uPickleToPlay)
    )

  implicit def playToUPickle(v: JsValue): Js.Value =
    v match {
      case JsString(x) =>
        Js.Str(x)
      case JsNumber(x) =>
        Js.Num(x.doubleValue)
      case JsBoolean(true) =>
        Js.True
      case JsBoolean(false) =>
        Js.False
      case JsObject(x) =>
        Js.Obj((x mapValues playToUPickle).toSeq: _*)
      case JsArray(x) =>
        Js.Arr(x map playToUPickle: _*)
      case JsNull =>
        Js.Null
    }

}
