package org.xanho.cube.akka

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import com.typesafe.scalalogging.LazyLogging
import org.xanho.utility.FutureUtility.FutureHelper

import scala.collection.mutable
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}
import scala.util.Success

/**
  * A Cube Cluster is a collection of Cube Actors operating on a single host.  The Cluster is responsible for
  * creating and destroying cube actors as instructed by the Cube Master
  *
  * @param id              The ID of the cluster
  * @param maximumCapacity The maximum number of cubes which can run in this cluster (generally limited by hardware)
  */
class CubeCluster(id: String,
                  maximumCapacity: Int) extends Actor with ActorLogging {

  import context.dispatcher

  /**
    * A reference to the Cube Master
    */
  private val cubeMaster: ActorRef =
    Await.result(context.actorSelection(masterPath).resolveOne(), defaultTimeout)

  /**
    * A mapping from actor ID to Cube Actor reference
    */
  private val cubeActors =
    mutable.Map.empty[String, ActorRef]

  context.system.scheduler.scheduleOnce(0.seconds)(
    cubeMaster ! CubeMaster.Messages.RegisterCluster(maximumCapacity)
  )

  def receive: Receive = {
    case CubeMaster.Messages.Mount(cubeIds) =>
      log.info(s"Received cube registration request for cube IDs: $cubeIds")
      cubeIds.foreach(loadCube)
      sender() ! Messages.Ok

    case CubeMaster.Messages.Dismount(cubeIds) =>
      log.info(s"Received cube unregister request for cube IDs: $cubeIds")
      Await.ready(
        Future.traverse(cubeIds)(unloadCube)
          .andThen {
            case Success(_) =>
              sender() ! Messages.Ok
            case _ =>
              sender() ! Messages.NotOk
          },
        Duration.Inf
      )

    case CubeMaster.Messages.UnregisterAll =>
      log.info("Received cube unregister all request")
      Await.ready(
        Future.traverse(cubeActors.keysIterator)(unloadCube)
          .andThen {
            case Success(_) =>
              sender() ! Messages.Ok
            case _ =>
              sender() ! Messages.NotOk
          },
        Duration.Inf
      )

    case Messages.Status =>
      log.info("Received status request")
      Future.traverse(cubeActors)(kv => kv._2 ? Messages.Status filter (_ == Messages.Ok))
        .await
      sender() ! CubeCluster.Messages.Status(cubeActors.keySet.toSet)
  }

  /**
    * Load a cube into this cluster, instantiating an actor as necessary.
    * If the cube ID already exists in this cluster, return its reference
    *
    * @param cubeId The ID of the cube
    * @return A reference to the actor
    */
  private def loadCube(cubeId: String): ActorRef =
    cubeActors.getOrElseUpdate(
      cubeId,
      context.actorOf(CubeActor.props(cubeId), cubeId)
    )

  override def postStop(): Unit =
    Future.traverse(cubeActors.keysIterator)(unloadCube)

  /**
    * Unload a cube from this actor by shutting down the cube actor and removing it from the mapping
    *
    * @param cubeId The ID of the cube to unload
    * @return A future
    */
  private def unloadCube(cubeId: String): Future[_] = {
    import akka.pattern.gracefulStop

    cubeActors
      .get(cubeId)
      .map(gracefulStop(_, 30.seconds))
      .map(_.andThen { case _ => cubeActors.remove(cubeId) })
      .getOrElse(Future.successful())
  }
}

object CubeCluster extends LazyLogging {

  def props(id: String,
            maximumCapacity: Int): Props =
    Props(new CubeCluster(id, maximumCapacity))

  def initialize(id: String = UUID.randomUUID().toString,
                 maximumCapacity: Int = 20)
                (implicit system: ActorSystem = defaultSystem): Unit = {
    logger.info(s"Starting a Cube Cluster actor with ID $id")
    system.actorOf(props(id, maximumCapacity), s"$cubeClusterPrefix$id")
  }

  object Messages {

    case class Status(cubeIds: Set[String]) extends ActorMessage

  }

}