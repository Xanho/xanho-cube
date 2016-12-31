package org.xanho.cube.akka

import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import com.seancheatham.graph.adapters.memory.MutableGraph
import org.xanho.cube.core.Message
import org.xanho.utility.data.{Buckets, DocumentStorage}
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable
import scala.concurrent.{Await, Future}
import scala.util.Random

/**
  * The master cube actor, which maintains references to Cube Cluster actors.  The master also
  * holds a directory of every Cube in the system, mapping them to the corresponding clusters.
  * The master is also responsible for constructing new cubes and assigning them to clusters
  */
class CubeMaster extends Actor {

  import context.dispatcher

  /**
    * A mapping of cluster actors by ID
    */
  private val clusterActors: mutable.Map[String, ActorRef] =
    mutable.Map.empty

  /**
    * A mapping from cluster actor ID to a list of its active cube IDs
    */
  private val cubeClusterAssignments: mutable.Map[String, Seq[String]] =
    mutable.Map.empty

  /**
    * Receive requests to register clusters, unregister clusters, and create new Cubes
    */
  def receive: PartialFunction[Any, Unit] = {

    case CubeMaster.Messages.RegisterCluster(clusterId) =>
      clusterActors += (clusterId -> sender())
      balanceClusters()

    case CubeMaster.Messages.UnregisterCluster(clusterId) =>
      val orphans =
        cubeClusterAssignments(clusterId)
      clusterActors -= clusterId
      cubeClusterAssignments -= clusterId
      val remainingActors =
        clusterActors.keys.toVector

      Await.result(
        Future.sequence(
          remainingActors
            .zipWithIndex
            .map {
              case (actorId, index) =>
                val orphanSlice =
                  orphans.slice(
                    orphans.size / remainingActors.size * index,
                    orphans.size / remainingActors.size * (index + 1)
                  )
                assignToCluster(actorId, orphanSlice)
            }
        ),
        defaultTimeout
      )
    case CubeMaster.Messages.CreateCube =>
      val cubeId =
        createCube()
      assignToCluster(randomCluster(), Vector(cubeId))
        .onSuccess { case _ => sender() ! cubeId }

  }

  /**
    * Shut down each of the clusters
    */
  override def postStop(): Unit =
    Future.traverse(clusterActors.keysIterator)(unloadCluster)

  /**
    * Unload the given cluster ID, instructing it to shut down each of its
    *
    * @param clusterId The ID of the cluster to instruct to shut down
    * @return A future operation which unloads the cluster
    */
  private def unloadCluster(clusterId: String): Future[_] = {
    import akka.pattern.gracefulStop

    import scala.concurrent.duration._

    clusterActors.get(clusterId)
      .map(gracefulStop(_, 30.seconds))
      .map(
        _.flatMap {
          _ =>
            clusterActors.remove(clusterId)
            val orphanedCubes =
              cubeClusterAssignments(clusterId)
            assign(orphanedCubes)
        }
      )
      .getOrElse(Future())
  }

  /**
    * Assigns the given cube IDs to the given cluster.  Updates the cubeClusterAssignments map.
    *
    * @param clusterId The ID of the destination cluster
    * @param cubeIds   The cube IDs to assign
    * @return A future operation which assigns the given IDs
    */
  private def assignToCluster(clusterId: String,
                              cubeIds: Seq[String]): Future[_] =
    (clusterActors(clusterId) ? CubeCluster.Messages.Register(cubeIds))
      .map(
        _ =>
          cubeClusterAssignments
            .update(clusterId, (cubeClusterAssignments(clusterId) ++ cubeIds).distinct)
      )

  /**
    * Assigns the given cube IDs to arbitrary clusters
    *
    * @param cubeIds The cube IDs to assign
    * @return A future operation which assigns the given IDs
    */
  private def assign(cubeIds: Seq[String]): Future[_] = {

    val targetSize: Int =
      cubeClusterAssignments.values.map(_.size).sum / clusterActors.size

    val queue =
      mutable.Queue(cubeIds: _*)

    Future.traverse(
      cubeClusterAssignments
        .toSeq
        .sortBy(_._2.size)) {
      kv =>
        val toUnorphan =
          (0 until (targetSize - kv._2.size).max(0))
            .flatMap(_ => if (queue.nonEmpty) Some(queue.dequeue()) else None)

        assignToCluster(kv._1, toUnorphan)
    }
  }

  /**
    * Selects a random active cluster
    *
    * @return the ID of the random cluster
    */
  private def randomCluster() =
    clusterActors.keys.toSeq(Random.nextInt(clusterActors.size))

  /**
    * Balances out the clusters, attempting to assign an equal number of cubes to each
    */
  private def balanceClusters(): Unit = {

    val targetSize: Int =
      cubeClusterAssignments.values.map(_.size).sum / clusterActors.size

    val orphans =
      mutable.Queue(
        cubeClusterAssignments
          .flatMap {
            case (clusterId, cubeIds) =>
              val toOrphan =
                cubeIds.drop(targetSize)
              if (toOrphan.nonEmpty)
                Await.result(
                  clusterActors(clusterId) ?
                    CubeCluster.Messages.Unregister(toOrphan), defaultTimeout
                )
              toOrphan
          }
          .toVector: _*
      )

    Await.result(
      Future.sequence(
        cubeClusterAssignments
          .toSeq
          .sortBy(_._2.size)
          .map {
            case (clusterId, cubeIds) =>
              val toUnorphan =
                (0 until (targetSize - cubeIds.size).max(0))
                  .map(_ => orphans.dequeue())

              assignToCluster(clusterId, toUnorphan)
          }
      ),
      defaultTimeout
    )

    Await.result(
      Future.sequence(
        orphans.toVector
          .map(cubeId => assignToCluster(randomCluster(), Vector(cubeId)))
      ),
      defaultTimeout
    )

  }

  /**
    * Creates a new Cube in the database
    *
    * @return The ID of the new cube
    */
  private def createCube(): String =
    Await.result(
      DocumentStorage.default
        .append(Buckets.CUBES)(
          Json.obj(
            "messages" -> Seq.empty[Message],
            "graph" -> new MutableGraph()(),
            "data" -> Map.empty[String, JsValue]
          )
        ),
      defaultTimeout
    )
}

object CubeMaster {

  object Messages {

    case class RegisterCluster(clusterId: String)

    case class UnregisterCluster(clusterId: String)

    case object CreateCube

  }

}