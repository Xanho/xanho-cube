package org.xanho.utility.data

import java.io.InputStream

import com.google.cloud.storage.{BlobInfo, StorageOptions}

import scala.concurrent.{ExecutionContext, Future}

/**
  * An interface for storing binary-like items
  */
abstract class BinaryStorage {

  /**
    * Fetch the value located in the bucket at the given key path
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @return A Future optional Iterator of Bytes
    */
  def get(bucket: String, key: String*)(implicit ec: ExecutionContext): Future[Option[Iterator[Byte]]]

  /**
    * (Over)write the file located at the given path in the given bucket
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @param value  An iterator of bytes to store
    * @return A Future
    */
  def write(bucket: String, key: String*)(value: Iterator[Byte])(implicit ec: ExecutionContext): Future[Unit]

  /**
    * Delete the value located in the bucket at the given key path
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @return A Future
    */
  def delete(bucket: String, key: String*)(implicit ec: ExecutionContext): Future[Unit]

}

object GoogleCloudStorage extends BinaryStorage {

  private lazy val datastore =
    StorageOptions.getDefaultInstance.getService

  def get(bucket: String, key: String*)(implicit ec: ExecutionContext): Future[Option[Iterator[Byte]]] =
    Future(
      Option(datastore.get(bucket, key.keyify))
        .map(_.getContent().iterator)
    )

  def write(bucket: String, key: String*)(value: Iterator[Byte])(implicit ec: ExecutionContext): Future[Unit] =
    Future(
      datastore.create(
        BlobInfo.newBuilder(bucket, key.keyify).build(),
        new InputStream {
          def read(): Int =
            if (value.hasNext)
              value.next()
            else
              -1
        }
      )
    )

  def delete(bucket: String, key: String*)(implicit ec: ExecutionContext): Future[Unit] =
    Future(
      datastore.delete(bucket, key.keyify)
    )

  implicit class KeyHelper(key: Seq[String]) {
    def keyify: String =
      key mkString "."
  }

}