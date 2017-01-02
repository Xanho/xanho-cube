package org.xanho.cube.akka.api.models

import org.xanho.utility.data.DocumentStorage
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
  def get(id: String)(implicit ec: ExecutionContext): Future[Option[JsValue]] =
    DocumentStorage.default.get(bucket, id)

  /**
    * Write the given value to the entity with the given ID
    *
    * @param id The ID to get
    * @return A future
    */
  def write(id: String)(value: JsValue)(implicit ec: ExecutionContext): Future[_] =
    DocumentStorage.default.write(bucket, id)(value)

  /**
    * Merge the given value over the entity with the given ID
    *
    * @param id The ID to get
    * @return A future
    */
  def merge(id: String)(value: JsValue)(implicit ec: ExecutionContext): Future[_] =
    DocumentStorage.default.merge(bucket, id)(value)

  /**
    * Delete the entity with the given ID
    *
    * @param id The ID to delete
    * @return A future
    */
  def delete(id: String)(implicit ec: ExecutionContext): Future[_] =
    DocumentStorage.default.delete(bucket, id)

}
