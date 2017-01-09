package org.xanho.web.frontend.utility

import java.util.UUID

import org.xanho.web.frontend.js.{DataSnapshot, ImportedJS}
import upickle.Js

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.scalajs.js._
import scala.scalajs.js.JSConverters._
import scala.util.Try

object Database {

  private def firebaseDatabase =
    ImportedJS.firebase.database()

  private def ref(bucket: String, key: Seq[String]) =
    firebaseDatabase.ref((bucket +: key).mkString("/"))

  private def ref(path: String) =
    firebaseDatabase.ref(path)

  /**
    * Fetch the value located in the bucket at the given key path
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @return A Future optional value
    */
  def get(bucket: String, key: String*)(implicit ec: ExecutionContext): Future[Js.Value] = {
    val promise =
      Promise[Js.Value]()
    val onSuccess: js.Function1[DataSnapshot, Unit] =
      (snapshot: DataSnapshot) => {
        Option(snapshot.value()) match {
          case Some(value) =>
            val js =
              Try(upickle.json.readJs(value))
            println(js)
            promise complete js
          case _ =>
            promise failure new NoSuchElementException((bucket +: key).mkString("/"))
        }
        ()
      }
    val onFailure: js.Function1[js.Error, Unit] =
      (error: js.Error) => {
        promise failure new NoSuchElementException(error.message)
        ()
      }

    ref(bucket, key)
      .once(
        "value",
        Some(onSuccess).orUndefined,
        Some(onFailure).orUndefined,
        None.orUndefined
      )
    promise.future
  }

  def listen(bucket: String, key: String*)
            (handler: (String, Js.Value => Unit))
            (implicit ec: ExecutionContext): String = {
    val id =
      UUID.randomUUID().toString
    val onSuccess: js.Function1[DataSnapshot, Unit] =
      (snapshot: DataSnapshot) => {
        Option(snapshot.value())
          .flatMap(value => Try(upickle.json.readJs(value)).toOption)
          .foreach(handler._2)
        ()
      }
    val onFailure: js.Function1[js.Error, Unit] =
      (error: js.Error) => {
        throw new Exception(error.message)
        ()
      }
    ref(bucket, key)
      .on(
        handler._1,
        Some(onSuccess).orUndefined,
        Some(onFailure).orUndefined,
        None.orUndefined
      )
    id
  }

  /**
    * A specialized version of [[org.xanho.web.frontend.utility.Database#get(java.lang.String, scala.collection.Seq, scala.concurrent.ExecutionContext)]]
    * which fetches an array value as an Iterator
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @return A Future Iterator of values
    */
  def getCollection(bucket: String, key: String*)(implicit ec: ExecutionContext): Future[Iterator[Js.Value]] =
    ???

  /**
    * Write (overwriting if anything exists there already) the given value to the given
    * key path located in the given bucket
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @param value  The value to write
    * @return A Future
    */
  def write(bucket: String, key: String*)(value: Js.Value)(implicit ec: ExecutionContext): Future[_] =
    ???

  /**
    * Merge the given value into the given key path. A merge is performed by traversing into object paths, and overwriting
    * terminal node values.  However, any nodes which aren't touched remain as they were.
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @param value  The value to write
    * @return A Future
    */
  def merge(bucket: String, key: String*)(value: Js.Value)(implicit ec: ExecutionContext): Future[_] =
    ???

  /**
    * Delete the value located in the bucket at the given key path
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @return A Future
    */
  def delete(bucket: String, key: String*)(implicit ec: ExecutionContext): Future[_] =
    ???

  /**
    * Append the given value to the array located in the given bucket at the given key.
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @param value  The value to append
    * @return A Future containing either the ID or index of the appended item
    */
  def append(bucket: String, key: String*)(value: Js.Value)(implicit ec: ExecutionContext): Future[String] =
    ref(bucket, key)
      .push(
        JSON.parse(upickle.json.write(value)),
        None.orUndefined
      )
    .toFuture
    .map(_.key)

  object Listeners {

    def childAdded(f: Js.Value => Unit): (String, Js.Value => Unit) =
      ("child_added", f)

  }

}
