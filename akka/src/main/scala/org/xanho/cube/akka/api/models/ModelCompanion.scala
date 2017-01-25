package org.xanho.cube.akka.api.models

import com.seancheatham.storage.firebase.FirebaseDatabase
import play.api.libs.json.JsValue

import scala.concurrent.{ExecutionContext, Future}

abstract class ModelCompanion {

  val bucket: String

  /**
    * Get the entity with the given ID
    *
    * @param id The ID to get
    * @return A future with the optional value
    */
  def get(id: String)(implicit ec: ExecutionContext): Future[JsValue] =
    FirebaseDatabase().get(bucket, id)

  /**
    * Write the given value to the entity with the given ID
    *
    * @param id The ID to get
    * @return A future
    */
  def write(id: String)(value: JsValue)(implicit ec: ExecutionContext): Future[_] =
    FirebaseDatabase().write(bucket, id)(value)

  /**
    * Merge the given value over the entity with the given ID
    *
    * @param id The ID to get
    * @return A future
    */
  def merge(id: String)(value: JsValue)(implicit ec: ExecutionContext): Future[_] =
    FirebaseDatabase().merge(bucket, id)(value)

  /**
    * Delete the entity with the given ID
    *
    * @param id The ID to delete
    * @return A future
    */
  def delete(id: String)(implicit ec: ExecutionContext): Future[_] =
    FirebaseDatabase().delete(bucket, id)

}
