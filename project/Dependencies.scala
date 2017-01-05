import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._

object Dependencies {

  object versions {
    val play = "2.5.10"
    val udashVersion = "0.4.0"
    val udashJQueryVersion = "1.0.0"
    val logbackVersion = "1.1.3"
    val jettyVersion = "9.3.11.v20160721"
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

  val akka =
    Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.4.16",
      "com.typesafe.akka" %% "akka-remote" % "2.4.16"
    )

  val akkaHttp = {
    val version = "10.0.0"
    Seq(
      "com.typesafe.akka" %% "akka-http" % version,
      "com.typesafe.akka" %% "akka-http-testkit" % version,
      "de.heikoseeberger" %% "akka-http-play-json" % "1.10.1"
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
        "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.5.0"
      )
    )

  val frontendJSDeps =
    Def.setting(
      Seq[org.scalajs.sbtplugin.JSModuleID](
        "org.webjars" % "firebase" % "3.2.0" / "firebase.js"
      )
    )

  val backendDeps =
    Def.setting(
      Seq[ModuleID](
        "org.eclipse.jetty" % "jetty-server" % versions.jettyVersion,
        "org.eclipse.jetty" % "jetty-servlet" % versions.jettyVersion,
        "io.udash" %% "udash-rpc-backend" % versions.udashVersion,
        "org.eclipse.jetty.websocket" % "websocket-server" % versions.jettyVersion
      )
    )

}
