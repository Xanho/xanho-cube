import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._

object Dependencies {

  object versions {
    val play = "2.5.10"
    val udashVersion = "0.4.0"
    val udashJQueryVersion = "1.0.0"
    val logbackVersion = "1.1.3"
    val upickleVersion = "0.4.3"
  }

  val playJson =
    Seq(
      "com.typesafe.play" %% "play-json" % versions.play
    )

  val playWS =
    Seq(
      "com.typesafe.play" %% "play-ws" % versions.play
    )

  val typesafe =
    Seq(
      "com.typesafe" % "config" % "1.3.1"
    )

  val test =
    Seq(
      "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    )

  val logging =
    Seq(
      "ch.qos.logback" % "logback-classic" % "1.1.7",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
    )

  val akka = {
    val version = "2.4.16"
    Seq(
      "com.typesafe.akka" %% "akka-actor" % version,
      "com.typesafe.akka" %% "akka-remote" % version,
      "com.typesafe.akka" %% "akka-persistence" % version
    )
  }

  val akkaHttp = {
    val version = "10.0.0"
    Seq(
      "com.typesafe.akka" %% "akka-http" % version,
      "com.typesafe.akka" %% "akka-http-testkit" % version,
      "de.heikoseeberger" %% "akka-http-play-json" % "1.10.1",
      "com.lihaoyi" %% "upickle" % versions.upickleVersion
    )
  }

  val graph =
    Seq(
      "com.seancheatham" %% "graph-core" % "0.0.2-SNAPSHOT",
      "com.seancheatham" %% "graph-memory-adapter" % "0.0.2-SNAPSHOT"
    )

  val openNlp =
    Seq(
      "org.apache.opennlp" % "opennlp" % "1.6.0",
      "org.apache.opennlp" % "opennlp-tools" % "1.6.0"
    )

  val googleCloudStorage =
    Seq(
      "com.google.cloud" % "google-cloud-storage" % "0.8.0-beta"
    )

  val firebase =
    Seq(
      "com.google.firebase" % "firebase-admin" % "4.0.3"
    )

  val storage =
    Seq(
      "com.seancheatham" %% "storage-firebase" % "0.0.1"
    )

  val journal =
    Seq(
      "com.seancheatham" %% "firebase-persistence" % "0.0.2"
    )

  val crossDeps =
    Def.setting(
      Seq[ModuleID](
        "io.udash" %%% "udash-core-shared" % versions.udashVersion,
        "io.udash" %%% "udash-rpc-shared" % versions.udashVersion,
        "com.lihaoyi" %%% "upickle" % versions.upickleVersion
      )
    )

  val frontendDeps =
    Def.setting(
      Seq[ModuleID](
        "io.udash" %%% "udash-core-frontend" % versions.udashVersion,
        "io.udash" %%% "udash-jquery" % versions.udashJQueryVersion,
        "io.udash" %%% "udash-rpc-frontend" % versions.udashVersion,
        "com.github.japgolly.scalacss" %%% "core" % "0.5.0",
        "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.5.0",
        "eu.unicredit" %%% "shocon" % "0.1.7"
      )
    )

  val frontendJSDeps =
    Def.setting(Seq[org.scalajs.sbtplugin.JSModuleID]())

}
