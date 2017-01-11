package org.xanho.cube.akka

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import com.typesafe.scalalogging.LazyLogging
import org.xanho.utility.FutureUtility.FutureHelper

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

/**
  * The master cube actor, which maintains references to Cube Cluster actors.  The master also
  * holds a directory of every Cube in the system, mapping them to the corresponding clusters.
  * The master is also responsible for constructing new cubes and assigning them to clusters
  */
class CubeMaster extends Actor with ActorLogging {

  import context.dispatcher

  /**
    * A set of cluster IDs
    */
  private val clusters =
    mutable.Map.empty[ActorRef, (Int, Set[String])]

  /**
    * A queue of cubes which have been orphaned, and need a new parent
    */
  private val orphanedCubes =
    mutable.Queue.empty[String]

  /**
    * Every 5 seconds, assign any orphaned cubes
    */
  context.system.scheduler.schedule(5.seconds, 5.seconds)(
    if (orphanedCubes.nonEmpty)
      self ! CubeMaster.Messages.AssignOrphans
  )

  /**
    * Receive requests to register clusters, unregister clusters, and create new Cubes
    */
  def receive: Receive = {

    case CubeMaster.Messages.RegisterCluster(maximumCapacity) =>
      val s = sender()
      log.info(s"Received register request from $s")
      clusters.get(s) match {
        case Some((_, cubeIds)) =>
          orphanedCubes.enqueue(cubeIds.toSeq: _*)
        case _ =>
      }
      clusters.update(s, (maximumCapacity, Set.empty[String]))
      s ! Messages.Ok
      balanceClusters()

    case CubeMaster.Messages.UnregisterCluster =>
      val s = sender()
      log.info(s"Received unregister request from $s")
      orphanedCubes.enqueue(clusters(s)._2.toSeq: _*)
      clusters.remove(s)

    case CubeMaster.Messages.Mount(cubeIds) =>
      Future.traverse(clusters.keys)(unassignFromCluster(_, cubeIds))
        .map(_ => orphanedCubes.enqueue(cubeIds.toSeq: _*))
        .map(_ => self ! CubeMaster.Messages.AssignOrphans)
        .map(_ => sender() ! Messages.Ok)
        .await

    case CubeMaster.Messages.Dismount(cubeIds) =>
      Future.traverse(clusters.keys)(unassignFromCluster(_, cubeIds))
        .map(_ => sender() ! Messages.Ok)
        .await

    case CubeMaster.Messages.AssignOrphans =>
      log.info(s"Received assign orphans from ${sender()}")
      assignOrphans().await

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
        .map {
          _ =>
            orphanedCubes.enqueue(clusters(cluster)._2.toSeq: _*)
            clusters.remove(cluster)
        }
    else
      Future.successful()
  }

  /**
    * Tells a Cluster a message
    *
    * @param clusterId The ID of the cluster
    * @param message   The message to send
    */
  private def tellCluster(clusterId: String,
                          message: Any): Unit =
    clusterRef(clusterId) ! message

  /**
    * Asks a cluster a question, returning a future with the answer
    *
    * @param clusterId The ID of the cluster
    * @param question  The message to send
    * @return A Future with the answer
    */
  private def askCluster(clusterId: String,
                         question: Any): Future[Any] =
    clusterRef(clusterId) ? question

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
    val (max, currentCubeIds) =
      clusters(cluster)
    cluster.ask(CubeMaster.Messages.Mount(cubeIds))
      .map(
        _ =>
          clusters
            .update(cluster, (max, currentCubeIds ++ cubeIds))
      )
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

    cluster.ask(CubeMaster.Messages.Dismount(cubeIds))
      .andThen {
        case Success(_) =>
          val (max, currentCubeIds) =
            clusters(cluster)
          clusters.update(cluster, (max, currentCubeIds -- cubeIds))
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
            case (clusterId, (targetSize, cubeIds)) =>
              clusterId ->
                (0 until (targetSize - cubeIds.size).max(0).min(orphanedCubes.size))
                  .map(_ => orphanedCubes.dequeue())
                  .toSet
          }
      )((assignToCluster _).tupled)
    else {
      log.error("No clusters available, re-queueing orphans")
      Future.successful()
    }

  /**
    * Balances out the clusters, attempting to assign an equal number of cubes to each
    */
  private def balanceClusters(): Unit = {
    clusters
      .foreach {
        case (cluster, (targetSize, cubeIds)) =>
          val toOrphan =
            cubeIds.drop(targetSize)
          if (toOrphan.nonEmpty)
            cluster
              .ask(
                CubeMaster.Messages.Dismount(toOrphan)
              ).await
          orphanedCubes.enqueue(toOrphan.toSeq: _*)
      }

    self ! CubeMaster.Messages.AssignOrphans
  }

  private def clusterRef(clusterId: String) =
    context.actorSelection(clusterPath(clusterId)).resolveOne().await
}

object CubeMaster extends LazyLogging {

  def initialize()
                (implicit system: ActorSystem = defaultSystem): Unit = {
    logger.info("Starting a Cube Master actor")
    system.actorOf(Props[CubeMaster], "cube-master")
  }

  def ref(implicit context: ActorContext): Future[ActorRef] =
    context.actorSelection(masterPath).resolveOne()

  object Messages {

    case class RegisterCluster(maximumCapacity: Int) extends ActorMessage

    case object UnregisterCluster extends ActorMessage

    case class Mount(cubeIds: Set[String]) extends ActorMessage

    case class Dismount(cubeIds: Set[String]) extends ActorMessage

    case object UnregisterAll extends ActorMessage

    case object AssignOrphans extends ActorMessage

  }

}