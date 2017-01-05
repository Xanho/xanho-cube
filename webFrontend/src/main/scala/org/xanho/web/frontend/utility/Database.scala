package org.xanho.web.frontend.utility

import org.xanho.web.frontend.js.{DataSnapshot, ImportedJS}
import upickle.Js

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.Any.fromFunction1
import scala.scalajs.js.JSConverters._

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
  def get(bucket: String, key: String*)(implicit ec: ExecutionContext): Future[Option[Js.Value]] = {
    val promise =
      scala.concurrent.Promise[Option[Js.Value]]()
    val onSuccess: js.Function1[DataSnapshot, Unit] =
      (snapshot: DataSnapshot) => {
        promise complete ???
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
    ???

}

object DynamicHelper {

  def toJsonObject(value: js.Dynamic): Js.Obj =
    ???

}
