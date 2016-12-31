import sbt._

object Dependencies {

  object versions {
    val play = "2.5.10"
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
      "com.typesafe.akka" %% "akka-actor" % "2.4.14"
    )

  val akkaHttp = {
    val version = "10.0.0"
    Seq(
      "com.typesafe.akka" %% "akka-http" % version,
      "com.typesafe.akka" %% "akka-http-testkit" % version
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

}
