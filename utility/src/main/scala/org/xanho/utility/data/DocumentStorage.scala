package org.xanho.utility.data

import java.io.ByteArrayInputStream
import java.util.UUID

import com.google.firebase.database.{ChildEventListener, DataSnapshot, DatabaseError, ValueEventListener}
import com.google.firebase.tasks.{OnFailureListener, OnSuccessListener}
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import play.api.libs.json._

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * An interface for storing JSON-like documents
  */
abstract class DocumentStorage {

  /**
    * Fetch the value located in the bucket at the given key path
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @return A Future optional value
    */
  def get(bucket: String, key: String*)(implicit ec: ExecutionContext): Future[Option[JsValue]]

  /**
    * A specialized version of [[org.xanho.utility.data.DocumentStorage#get(java.lang.String, scala.collection.Seq)]]
    * which fetches an array value as an Iterator
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @return A Future Iterator of values
    */
  def getCollection(bucket: String, key: String*)(implicit ec: ExecutionContext): Future[Iterator[JsValue]]

  /**
    * Write (overwriting if anything exists there already) the given value to the given
    * key path located in the given bucket
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @param value  The value to write
    * @return A Future
    */
  def write(bucket: String, key: String*)(value: JsValue)(implicit ec: ExecutionContext): Future[_]

  /**
    * Merge the given value into the given key path. A merge is performed by traversing into object paths, and overwriting
    * terminal node values.  However, any nodes which aren't touched remain as they were.
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @param value  The value to write
    * @return A Future
    */
  def merge(bucket: String, key: String*)(value: JsValue)(implicit ec: ExecutionContext): Future[_]

  /**
    * Delete the value located in the bucket at the given key path
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @return A Future
    */
  def delete(bucket: String, key: String*)(implicit ec: ExecutionContext): Future[_]

  /**
    * Append the given value to the array located in the given bucket at the given key.
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @param value  The value to append
    * @return A Future containing either the ID or index of the appended item
    */
  def append(bucket: String, key: String*)(value: JsValue)(implicit ec: ExecutionContext): Future[String]

}

object DocumentStorage {
  def apply(): DocumentStorage =
    default
  def default: DocumentStorage =
    FirebaseDatabase
}

object FirebaseDatabase extends DocumentStorage {

  import org.xanho.utility.Config.config

  val baseUrl: String =
    config.getString("firebase.url")
      .ensuring(_ startsWith "https://")

  private val firebaseConfiguration =
    Json.obj(
      "type" -> "service_account",
      "project_id" -> config.getString("firebase.project_id"),
      "private_key_id" -> config.getString("firebase.private_key_id"),
      "private_key" -> config.getString("firebase.private_key"),
      "client_email" -> config.getString("firebase.client_email"),
      "client_id" -> config.getString("firebase.client_id"),
      "auth_uri" -> "https://accounts.google.com/o/oauth2/auth",
      "token_uri" -> "https://accounts.google.com/o/oauth2/token",
      "auth_provider_x509_cert_url" -> "https://www.googleapis.com/oauth2/v1/certs",
      "client_x509_cert_url" -> config.getString("firebase.client_x509_cert_url")
    )

  private val app =
    FirebaseApp.initializeApp(
      new FirebaseOptions.Builder()
        .setServiceAccount(new ByteArrayInputStream(firebaseConfiguration.toString.toArray.map(_.toByte)))
        .setDatabaseUrl(baseUrl)
        .build()
    )

  private val database =
    com.google.firebase.database.FirebaseDatabase.getInstance(app)

  def get(bucket: String, key: String*)(implicit ec: ExecutionContext): Future[Option[JsValue]] = {
    val p = Promise[Option[JsValue]]()
    database.getReference((bucket +: key).keyify)
      .addListenerForSingleValueEvent(
        new ValueEventListener {
          def onDataChange(dataSnapshot: DataSnapshot): Unit =
            p success anyToJson(dataSnapshot.getValue()).toOption

          def onCancelled(databaseError: DatabaseError): Unit =
            p failure new IllegalStateException(databaseError.getMessage)
        }
      )
    p.future
  }

  def getCollection(bucket: String, key: String*)(implicit ec: ExecutionContext): Future[Iterator[JsValue]] =
    get(bucket, key: _*)
      .map {
        case Some(v: JsArray) =>
          v.value.iterator
        case Some(v: JsObject) =>
          v.fields.sortBy(_._1).map(_._2).iterator
        case _ =>
          Iterator.empty
      }

  def write(bucket: String, key: String*)(value: JsValue)(implicit ec: ExecutionContext): Future[_] = {
    val p = Promise[Any]()
    database.getReference((bucket +: key).keyify)
      .setValue(jsonToAny(value))
      .addOnSuccessListener(new OnSuccessListener[Void] {
        def onSuccess(tResult: Void): Unit =
          p success Unit
      })
      .addOnFailureListener(new OnFailureListener {
        def onFailure(e: Exception): Unit =
          p failure e
      })
    p.future
  }

  def merge(bucket: String, key: String*)(value: JsValue)(implicit ec: ExecutionContext): Future[_] = {
    val p = Promise[Any]()
    val reference =
      database.getReference((bucket +: key).keyify)
    (value match {
      case v: JsObject =>
        reference.updateChildren(jsonToAny(v).asInstanceOf[java.util.Map[String, AnyRef]])
      case v =>
        reference.setValue(jsonToAny(v))
    })
      .addOnSuccessListener(new OnSuccessListener[Void] {
        def onSuccess(tResult: Void): Unit =
          p success Unit
      })
      .addOnFailureListener(new OnFailureListener {
        def onFailure(e: Exception): Unit =
          p failure e
      })
    p.future
  }

  def delete(bucket: String, key: String*)(implicit ec: ExecutionContext): Future[_] = {
    val p = Promise[Any]()
    database.getReference((bucket +: key).keyify)
      .removeValue()
      .addOnSuccessListener(new OnSuccessListener[Void] {
        def onSuccess(tResult: Void): Unit =
          p success Unit
      })
      .addOnFailureListener(new OnFailureListener {
        def onFailure(e: Exception): Unit =
          p failure e
      })
    p.future
  }

  def append(bucket: String, key: String*)(value: JsValue)(implicit ec: ExecutionContext): Future[String] = {
    val p = Promise[String]()
    val reference =
      database.getReference((bucket +: key).keyify)
    reference
      .push()
      .setValue(jsonToAny(value))
      .addOnSuccessListener(new OnSuccessListener[Void] {
        def onSuccess(tResult: Void): Unit =
          p success reference.getKey
      })
      .addOnFailureListener(new OnFailureListener {
        def onFailure(e: Exception): Unit =
          p failure e
      })
    p.future
  }

  private val collectionWatchers =
    TrieMap.empty[String, (ChildEventListener, String)]

  /**
    * Firebase provides functionality to attach listeners to array-like nodes in its Realtime Database.  This method
    * attaches a listener to the node at the given bucket+key, and when a new item is added, the function f is called.
    *
    * NOTE: This method has a side-effect
    *
    * TODO: How do we ensure old/unused listeners get cleaned up when a client terminates
    *
    * @param bucket The bucket to search
    * @param key    The key path to the item
    * @param f      A function which handles a newly added JsValue
    * @return An identifier for the watcher, to be used when removing the watcher
    */
  def watchCollection(bucket: String, key: String*)(f: JsValue => _): String = {
    val id =
      UUID.randomUUID().toString
    val listener =
      new ChildEventListener {
        def onChildRemoved(dataSnapshot: DataSnapshot): Unit = {}

        def onChildMoved(dataSnapshot: DataSnapshot, s: String): Unit = {}

        def onChildChanged(dataSnapshot: DataSnapshot, s: String): Unit = {}

        def onCancelled(databaseError: DatabaseError): Unit = {}

        def onChildAdded(dataSnapshot: DataSnapshot, s: String): Unit = {
          anyToJson(dataSnapshot.getValue())
            .toOption
            .foreach(f)
        }
      }
    val keyified =
      (bucket +: key).keyify
    collectionWatchers.update(id, (listener, keyified))
    database.getReference(keyified)
      .addChildEventListener(listener)
    id
  }

  /**
    * Removes a watcher by ID, as constructed in #watchCollection(...)
    *
    * NOTE: This method has a side-effect
    *
    * @param id The ID of the watcher provided in #watchCollection(...)
    * @return Unit
    */
  def unwatchCollection(id: String): Unit =
    collectionWatchers.remove(id)
      .foreach(
        kv =>
          database.getReference(kv._2)
            .removeEventListener(kv._1)
      )

  implicit class KeyHelper(key: Seq[String]) {
    def keyify: String =
      key mkString "/"
  }

  implicit class JsHelper(v: JsValue) {
    def toOption: Option[JsValue] =
      v match {
        case JsNull =>
          None
        case x =>
          Some(x)
      }
  }

  private def anyToJson(any: Any): JsValue =
    any match {
      case null =>
        JsNull
      case v: Double =>
        JsNumber(v)
      case v: Long =>
        JsNumber(v)
      case s: String =>
        JsString(s)
      case v: Boolean =>
        JsBoolean(v)
      case v: java.util.HashMap[String@unchecked, _] =>
        import scala.collection.JavaConverters._
        JsObject(v.asScala.mapValues(anyToJson))
      case v: java.util.ArrayList[_] =>
        import scala.collection.JavaConverters._
        JsArray(v.asScala.map(anyToJson))
    }

  private def jsonToAny(json: JsValue): Any =
    json match {
      case JsNull =>
        null
      case v: JsNumber =>
        val long =
          v.value.longValue()
        val double =
          v.value.doubleValue()
        if (long == double)
          long
        else
          double
      case v: JsString =>
        v.value
      case v: JsBoolean =>
        v.value
      case v: JsArray =>
        import scala.collection.JavaConverters._
        v.value.toVector.map(jsonToAny).asJava
      case v: JsObject =>
        import scala.collection.JavaConverters._
        v.value.mapValues(jsonToAny).asJava
    }

}