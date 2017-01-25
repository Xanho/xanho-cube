package org.xanho.cube.akka

import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ActorContext, ActorLogging, ActorRef, ActorSelection, ActorSystem, ExtendedActorSystem, Props}
import akka.pattern.ask
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import com.typesafe.scalalogging.LazyLogging
import org.xanho.utility.FutureUtility.FutureHelper
import play.api.libs.json._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * The master cube actor, which maintains references to Cube Cluster actors.  The master also
  * holds a directory of every Cube in the system, mapping them to the corresponding clusters.
  * The master is also responsible for constructing new cubes and assigning them to clusters
  */
class CubeMaster extends PersistentActor with ActorLogging {

  import context.dispatcher

  override def persistenceId =
    "cube-master"

  /**
    * A count of events which have been processed by this actor
    */
  private val eventCount =
    new AtomicLong()

  private implicit val config =
    context.system.settings.config

  /**
    * The number events to wait between saving snapshots
    */
  private val saveSnapshotFrequency: Int =
    if(config.hasPath("persistence.frequency"))
      config.getInt("persistence.frequency")
    else
      8

  /**
    * A set of cluster IDs
    */
  private val clusters =
    mutable.Map.empty[ActorRef, (Int, Set[String])]

  /**
    * A queue of cubes which have been orphaned, and need a new parent
    */
  private val orphanedCubes =
    mutable.Set.empty[String]

  /**
    * Every 5 seconds, assign any orphaned cubes
    */
  override def preStart(): Unit =
    context.system.scheduler.scheduleOnce(5.seconds, self, CubeMaster.Messages.AssignOrphans)

  override def postRestart(reason: Throwable): Unit = {}

  /**
    * Receive requests to register clusters, unregister clusters, and create new Cubes
    */
  val receiveCommand: Receive = {

    case CubeMaster.Messages.RegisterCluster(maximumCapacity) =>
      val s =
        sender()
      log.info(s"Received register request from $s")
      if (clusters contains s)
        orphanAndRemoveCluster(s)
      persistAndApply(CubeMaster.Events.AddCluster(s, maximumCapacity)) { _ =>
        s ! Messages.Ok
        balanceClusters().await
      }

    case CubeMaster.Messages.UnregisterCluster =>
      val s =
        sender()
      log.info(s"Received unregister request from $s")
      orphanAndRemoveCluster(s)
      s ! Messages.Ok
      balanceClusters().await

    case CubeMaster.Messages.Mount(cubeIds) =>
      val s =
        sender()
      // We only want to mount cubes which aren't already mounted yet
      val corrected =
        clusters.foldLeft(cubeIds)(_ -- _._2._2)
      if (corrected.nonEmpty)
        persistAndApply(CubeMaster.Events.AddOrphans(corrected))(_ => ())

      s ! Messages.Ok

    case CubeMaster.Messages.Dismount(cubeIds) =>
      val s =
        sender()

      // Construct a mapping from Cluster -> Cubes to Dismount
      val toDismount =
        clusters
          .mapValues(_._2 intersect cubeIds)
          .filter(_._2.nonEmpty)

      Future.traverse(toDismount)((unassignFromCluster _).tupled).await

      s ! Messages.Ok

    case CubeMaster.Messages.AssignOrphans =>
      if (orphanedCubes.nonEmpty)
        assignOrphans().await
      context.system.scheduler.scheduleOnce(5.seconds, self, CubeMaster.Messages.AssignOrphans)

    case e: Exception =>
      throw e

  }

  private var isRecovering =
    true

  val receiveRecover: Receive = {
    case SnapshotOffer(_, v) =>
      val (snapshotClusters, snapshotOrphanedCubes) =
        v
      clusters.clear()
      snapshotClusters.asInstanceOf[mutable.Map[ActorRef, (Int, Set[String])]].foreach(clusters += _)
      orphanedCubes.clear()
      orphanedCubes ++= snapshotOrphanedCubes.asInstanceOf[mutable.Set[String]]
    case e: CubeMaster.CubeMasterEvent =>
      updateState(e)
    case RecoveryCompleted =>
      isRecovering = false
      heal().await(20.seconds)
    case e =>
      log.warning(s"Unrecognized journal event: $e")
  }

  private def updateState(event: CubeMaster.CubeMasterEvent): Unit = {
    event match {

      case CubeMaster.Events.AddCluster(cluster, maximumCapacity) =>
        clusters.update(cluster, (maximumCapacity, Set.empty))
      case CubeMaster.Events.RemoveCluster(cluster) =>
        clusters.remove(cluster)

      case CubeMaster.Events.MountCubes(cluster, cubeIds) =>
        val (maxCapacity, previousCubeIds) =
          clusters(cluster)
        clusters.update(
          cluster,
          (maxCapacity, previousCubeIds ++ cubeIds)
        )
      case CubeMaster.Events.DismountCubes(cluster, cubeIds) =>
        val (maxCapacity, previousCubeIds) =
          clusters(cluster)
        clusters.update(
          cluster,
          (maxCapacity, previousCubeIds -- cubeIds)
        )

      case CubeMaster.Events.AddOrphans(cubeIds) =>
        orphanedCubes ++= cubeIds
      case CubeMaster.Events.RemoveOrphans(cubeIds) =>
        orphanedCubes --= cubeIds

    }
    val newEventCount =
      eventCount.incrementAndGet()
    if(
      !isRecovering &&
        newEventCount % saveSnapshotFrequency == 0
    )
      saveSnapshot((clusters, orphanedCubes))
  }

  /**
    * Shut down each of the clusters
    */
  override def postStop(): Unit =
    Future.traverse(clusters.keys)(unloadCluster).await

  /**
    * Unload the given cluster ID, instructing it to shut down each of its
    *
    * @param cluster The ID of the cluster to instruct to shut down
    * @return A future operation which unloads the cluster
    */
  private def unloadCluster(cluster: ActorRef): Future[_] = {
    import akka.pattern.gracefulStop

    if (clusters contains cluster)
      gracefulStop(cluster, 30.seconds)
        .map(_ => orphanAndRemoveCluster(cluster))
    else
      Future.successful()
  }

  private def orphanAndRemoveCluster(cluster: ActorRef): Unit = {
    clusters(cluster)._2 match {
      case s if s.nonEmpty =>
        persistAndApply(CubeMaster.Events.AddOrphans(clusters(cluster)._2))(_ => ())
      case _ =>
    }
    persistAndApply(CubeMaster.Events.RemoveCluster(cluster))(_ => ())
  }

  /**
    * Assigns the given cube IDs to the given cluster.  Updates the cubeClusterAssignments map.
    *
    * @param cluster The reference to the actor to assign to
    * @param cubeIds The cube IDs to assign
    * @return A future operation which assigns the given IDs
    */
  private def assignToCluster(cluster: ActorRef,
                              cubeIds: Set[String]): Future[_] = {
    log.info(s"Assigning cubes to cluster $cluster: $cubeIds")
    val toAssign =
      cubeIds -- clusters(cluster)._2
    if (toAssign.nonEmpty) {
      persistAndApply(CubeMaster.Events.MountCubes(cluster, toAssign))(_ => ())
      cluster
        .ask(CubeMaster.Messages.Mount(toAssign))
    } else {
      Future.successful()
    }
  }

  /**
    * Assigns the given cube IDs to the given cluster.  Updates the cubeClusterAssignments map.
    *
    * @param cluster The reference to the actor to unassign from
    * @param cubeIds The cube IDs to assign
    * @return A future operation which assigns the given IDs
    */
  private def unassignFromCluster(cluster: ActorRef,
                                  cubeIds: Set[String]): Future[_] = {
    log.info(s"Unassigning cubes from cluster $cluster: $cubeIds")
    val toUnassign =
      cubeIds intersect clusters(cluster)._2
    if (toUnassign.nonEmpty) {
      persistAndApply(CubeMaster.Events.DismountCubes(cluster, toUnassign))(_ => ())
      cluster
        .ask(CubeMaster.Messages.Dismount(toUnassign))
    } else {
      Future.successful()
    }
  }

  /**
    * Assigns all currently orphaned cubes to clusters, balancing in the process
    *
    * @return a Future
    */
  private def assignOrphans(): Future[_] =
    if (clusters.nonEmpty)
      Future.traverse(
        clusters
          .toSeq
          .sortBy(_._2._2.size)
          .map {
            case (cluster, (targetSize, cubeIds)) =>
              cluster ->
                orphanedCubes
                  .take(
                    (targetSize - cubeIds.size)
                      .max(0)
                      .min(orphanedCubes.size)
                  )
                  .toSet
          }
          .collect {
            case (cluster, toUnorphan) if toUnorphan.nonEmpty =>
              persistAndApply(CubeMaster.Events.RemoveOrphans(toUnorphan))(_ => ())
              cluster -> toUnorphan
          }
      )((assignToCluster _).tupled)
    else {
      log.error("No clusters available, re-queueing orphans")
      Future.successful()
    }

  /**
    * Balances out the clusters, attempting to assign an equal number of cubes to each
    */
  private def balanceClusters(): Future[_] =
    Future.traverse(
      clusters
        .mapValues { case (targetSize, cubeIds) => cubeIds drop targetSize }
        .collect {
          case (cluster, toOrphan) if toOrphan.nonEmpty =>
            persistAndApply(CubeMaster.Events.DismountCubes(cluster, toOrphan))(_ =>
              persistAndApply(CubeMaster.Events.AddOrphans(toOrphan))(_ => Unit)
            )
            cluster -> CubeMaster.Messages.Dismount(toOrphan)
        }
    ) {
      case (cluster, dismountMessage) =>
        (cluster ? dismountMessage).filter(_ == Messages.Ok)
    }

  /**
    * Persist the given event, handle it, and run the given handler asynchronously
    *
    * @param event   The event to persist and handle
    * @param handler The handler to apply afterwards
    * @tparam A a Cube Master Event
    */
  private def persistAndApply[A <: CubeMaster.CubeMasterEvent](event: A)(handler: A => Unit) =
    persist(event) {
      e =>
        updateState(e)
        handler(e)
    }

  /**
    * Performs a self-healing process by pinging each cluster and gathering its current active cubes.  Any mis-alignments
    * from what is expected will be handled.  If the cluster does not respond, generate new events which remove the
    * cluster and its cubes.
    *
    * @return a Future
    */
  private def heal(): Future[_] = {
    Future.traverse(clusters) {
      case (cluster, (_, cubeIds)) =>
        cluster
          // Request its status
          .ask(Messages.Status)(5.seconds)
          .collect {
            // We only care about Status responses
            case CubeCluster.Messages.Status(actualCubeIds) =>
              (cubeIds -- actualCubeIds, actualCubeIds -- cubeIds)
          }
          .flatMap {
            // (Cubes missing from Cluster, Cubes on cluster which were not expected)
            case (missing, extras) =>
              val missingFuture =
                if (missing.nonEmpty)
                  cluster
                    .ask(CubeMaster.Messages.Mount(missing))
                    .filter(_ == Messages.Ok)
                else
                  Future.successful()
              val extraFuture =
                if (extras.nonEmpty)
                  cluster
                    .ask(CubeMaster.Messages.Dismount(extras))
                    .filter(_ == Messages.Ok)
                else
                  Future.successful()
              missingFuture.zip(extraFuture)
          }
          .recoverWith {
            // If the cluster could not be found, or if something error'd, we'll remove it and orphan its cubes
            case _ =>
              val expectedCubes =
                clusters(cluster)._2
              if (expectedCubes.nonEmpty) {
                persistAndApply(CubeMaster.Events.DismountCubes(cluster, expectedCubes))(_ => ())
                persistAndApply(CubeMaster.Events.AddOrphans(expectedCubes))(_ => ())
              }
              persistAndApply(CubeMaster.Events.RemoveCluster(cluster))(_ => ())
              Future.successful()
          }
    }
  }
}

object CubeMaster extends LazyLogging {

  def initialize()
                (implicit system: ActorSystem = defaultSystem): Unit = {
    logger.info("Starting a Cube Master actor")
    system.actorOf(Props[CubeMaster], "cube-master")
  }

  def ref(implicit context: ActorContext): ActorSelection =
    context.actorSelection(masterPath)

  object Messages {

    case class RegisterCluster(maximumCapacity: Int) extends ActorMessage with ActorEvent

    case object UnregisterCluster extends ActorMessage with ActorEvent

    case class Mount(cubeIds: Set[String]) extends ActorMessage with ActorEvent

    case class Dismount(cubeIds: Set[String]) extends ActorMessage with ActorEvent

    case object UnregisterAll extends ActorMessage

    case object AssignOrphans extends ActorMessage

  }

  sealed abstract class CubeMasterEvent extends ActorEvent

  import akka.serialization._

  implicit def formatActorRef(implicit system: ExtendedActorSystem): Format[ActorRef] =
    Format[ActorRef](
      Reads[ActorRef](v => JsSuccess(system.provider.resolveActorRef(v.as[String]))),
      Writes[ActorRef](ref => JsString(Serialization.serializedActorPath(ref)))
    )

  implicit def format(implicit system: ExtendedActorSystem): Format[CubeMasterEvent] =
    Format[CubeMasterEvent](
      Reads[CubeMasterEvent](v =>
        JsSuccess(
          (v \ "type").as[String] match {
            case "addCluster" =>
              v.as[Events.AddCluster](Json.reads[Events.AddCluster])
            case "removeCluster" =>
              v.as[Events.RemoveCluster](Json.reads[Events.RemoveCluster])
            case "mountCubes" =>
              v.as[Events.MountCubes](Json.reads[Events.MountCubes])
            case "dismountCubes" =>
              v.as[Events.DismountCubes](Json.reads[Events.DismountCubes])
            case "addOrphans" =>
              v.as[Events.AddOrphans](Json.reads[Events.AddOrphans])
            case "removeOrphans" =>
              v.as[Events.RemoveOrphans](Json.reads[Events.RemoveOrphans])
          }
        )
      ),
      Writes[CubeMasterEvent] {
        case e: Events.AddCluster =>
          Json.toJson(e)(Json.writes[Events.AddCluster]).as[JsObject] +
            ("type" -> JsString("addCluster"))
        case e: Events.RemoveCluster =>
          Json.toJson(e)(Json.writes[Events.RemoveCluster]).as[JsObject] +
            ("type" -> JsString("removeCluster"))
        case e: Events.MountCubes =>
          Json.toJson(e)(Json.writes[Events.MountCubes]).as[JsObject] +
            ("type" -> JsString("mountCubes"))
        case e: Events.DismountCubes =>
          Json.toJson(e)(Json.writes[Events.DismountCubes]).as[JsObject] +
            ("type" -> JsString("dismountCubes"))
        case e: Events.AddOrphans =>
          Json.toJson(e)(Json.writes[Events.AddOrphans]).as[JsObject] +
            ("type" -> JsString("addOrphans"))
        case e: Events.RemoveOrphans =>
          Json.toJson(e)(Json.writes[Events.RemoveOrphans]).as[JsObject] +
            ("type" -> JsString("removeOrphans"))
      }
    )

  object Events {

    case class AddCluster(cluster: ActorRef, maximumCapacity: Int) extends CubeMasterEvent

    case class RemoveCluster(cluster: ActorRef) extends CubeMasterEvent

    case class MountCubes(cluster: ActorRef, cubeIds: Set[String]) extends CubeMasterEvent

    case class DismountCubes(cluster: ActorRef, cubeIds: Set[String]) extends CubeMasterEvent

    case class AddOrphans(cubeIds: Set[String]) extends CubeMasterEvent

    case class RemoveOrphans(cubeIds: Set[String]) extends CubeMasterEvent

  }

}