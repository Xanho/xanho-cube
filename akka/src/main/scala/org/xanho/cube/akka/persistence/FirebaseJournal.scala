package org.xanho.cube.akka.persistence

import java.util.concurrent.Executors

import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}
import com.google.firebase.database.{DataSnapshot, DatabaseError, ValueEventListener}
import org.xanho.cube.akka.CubeMaster
import org.xanho.utility.data.FirebaseDatabase
import play.api.libs.json.{JsValue, Json}

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Success, Try}

class FirebaseJournal extends AsyncWriteJournal {

  private implicit val ec =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))

  private val persistenceKeyPath: String =
    "infrastructure/akka/persistence"

  private implicit val actorSystem =
    persistence.system

  def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] =
    Future.traverse(messages) {
      case message if message.payload.size != 1 =>
        throw new UnsupportedOperationException("persistAll is currently not supported in Firebase Journal")
      case message =>
        val repr =
          message.payload.head
        FirebaseDatabase.write(
          persistenceKeyPath, message.persistenceId, "events", repr.sequenceNr.toString
        )(Json.toJson(repr.payload.asInstanceOf[CubeMaster.CubeMasterEvent]))
          .map(_ => Success())
    }

  def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] =
    FirebaseDatabase.get(persistenceKeyPath, persistenceId, "events")
      .flatMap {
        case Some(v) =>
          val toDelete =
            v.as[Map[String, JsValue]]
              .keysIterator
              .map(_.toLong)
              .filter(_ <= toSequenceNr)
          Future.traverse(toDelete)(key =>
            FirebaseDatabase.delete(persistenceKeyPath, persistenceId, "events", key.toString)
          )
            .map(_ => ())
        case _ =>
          Future.successful()
      }

  def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)
                         (recoveryCallback: (PersistentRepr) => Unit): Future[Unit] = {
    val promise =
      Promise[Unit]()
    val watchId =
      FirebaseDatabase
        .watchCollection(persistenceKeyPath, persistenceId, "events")(
          (messageId: String, v: JsValue) =>
            messageId.toLong match {
              case id if id >= fromSequenceNr && id <= toSequenceNr =>
                val repr =
                  PersistentRepr(
                    v.as[CubeMaster.CubeMasterEvent],
                    id,
                    persistenceId
                  )
                recoveryCallback(repr)
                if(id == toSequenceNr)
                  promise success ()
              case id if id > toSequenceNr =>
                promise success()
              case _ =>
            }
        )
    promise.future.andThen { case _ => FirebaseDatabase.unwatchCollection(watchId) }
  }

  def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    import org.xanho.utility.data.FirebaseDatabase.KeyHelper
    val promise =
      Promise[Long]()

    FirebaseDatabase.database.getReference(
      Seq(persistenceKeyPath, persistenceId, "events").keyify
    ).limitToLast(1)
      .addListenerForSingleValueEvent(
        new ValueEventListener {
          def onDataChange(dataSnapshot: DataSnapshot): Unit = {
            val i = dataSnapshot.getChildren.iterator()
            val id =
              if (i.hasNext)
                i.next().getKey.toLong
              else
                0l
            promise success id
          }

          def onCancelled(databaseError: DatabaseError): Unit =
            promise failure new IllegalStateException(databaseError.getMessage)
        }
      )
    promise.future
  }
}
