package org.xanho.cube.akka

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.ask
import com.seancheatham.graph.adapters.memory.MutableGraph
import com.typesafe.scalalogging.LazyLogging
import org.xanho.cube.core.Message
import org.xanho.utility.FutureUtility.FutureHelper
import org.xanho.utility.data.{Buckets, DocumentStorage}
import play.api.libs.json.Json

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

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
    mutable.Map.empty[String, (Int, Set[String])]

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

    case CubeMaster.Messages.RegisterCluster(clusterId, maximumCapacity) =>
      log.info(s"Received register request with ID $clusterId from ${sender()}")
      clusters.update(clusterId, (maximumCapacity, Set.empty[String]))
      sender() ! Messages.Ok
      balanceClusters()

    case CubeMaster.Messages.UnregisterCluster(clusterId) =>
      log.info(s"Received unregister request with ID $clusterId from ${sender()}")
      orphanedCubes.enqueue(clusters(clusterId)._2.toSeq: _*)
      clusters.remove(clusterId)

    case CubeMaster.Messages.CreateCube(ownerId) =>
      log.info(s"Received create cube request from ${sender()}")
      val cubeId =
        createCube(ownerId)
      orphanedCubes.enqueue(cubeId)
      sender() ! cubeId

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
    * @param clusterId The ID of the cluster to instruct to shut down
    * @return A future operation which unloads the cluster
    */
  private def unloadCluster(clusterId: String): Future[_] = {
    import akka.pattern.gracefulStop

    if (clusters contains clusterId)
      gracefulStop(clusterRef(clusterId), 30.seconds)
        .map {
          _ =>
            orphanedCubes.enqueue(clusters(clusterId)._2.toSeq: _*)
            clusters.remove(clusterId)
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
    * @param clusterId The ID of the destination cluster
    * @param cubeIds   The cube IDs to assign
    * @return A future operation which assigns the given IDs
    */
  private def assignToCluster(clusterId: String,
                              cubeIds: Set[String]): Future[_] = {
    log.info(s"Assigning cubes to cluster $clusterId: $cubeIds")
    val (max, currentCubeIds) =
      clusters(clusterId)
    askCluster(clusterId, CubeCluster.Messages.Register(cubeIds))
      .map(
        _ =>
          clusters
            .update(clusterId, (max, currentCubeIds ++ cubeIds))
      )
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
        case (clusterId, (targetSize, cubeIds)) =>
          val toOrphan =
            cubeIds.drop(targetSize)
          if (toOrphan.nonEmpty)
            askCluster(
              clusterId,
              CubeCluster.Messages.Unregister(toOrphan)
            ).await
          orphanedCubes.enqueue(toOrphan.toSeq: _*)
      }

    self ! CubeMaster.Messages.AssignOrphans
  }

  /**
    * Creates a new Cube in the database
    *
    * @return The ID of the new cube
    */
  private def createCube(ownerId: String): String = {
    val id =
      UUID.randomUUID().toString
    DocumentStorage.default
      .write(Buckets.CUBES, id)(
        Json.obj(
          "messages" -> Seq.empty[Message],
          "graph" -> new MutableGraph()(),
          "data" -> Json.obj(
            "creationDate" -> System.currentTimeMillis(),
            "ownerId" -> ownerId
          )
        )
      )
      .map(_ => id)
      .await
  }

  private def clusterRef(clusterId: String) =
    context.actorSelection(
      clusterPath(clusterId)
    ).resolveOne()
      .await
}

object CubeMaster extends LazyLogging {

  def initialize(): Unit = {
    logger.info("Starting a Cube Master actor")
    val system =
      ActorSystem("xanho")
    val actor =
      system.actorOf(Props[CubeMaster], "cube-master")
  }

  object Messages {

    case class RegisterCluster(clusterId: String,
                               maximumCapacity: Int)

    case class UnregisterCluster(clusterId: String)

    case class CreateCube(ownerId: String)

    case object AssignOrphans

  }

}