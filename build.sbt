lazy val commonSettings =
  Seq(
    organization := "org.xanho",
    scalaVersion := "2.11.8",
    libraryDependencies ++=
      Dependencies.playJson ++
        Dependencies.typesafe ++
        Dependencies.test ++
        Dependencies.logging
  )

lazy val root =
  project
    .in(file("."))
    .settings(commonSettings: _*)
    .settings(packagedArtifacts := Map.empty)
    .aggregate(utility, cubeCore, cubeAkka)

lazy val utility =
  project
    .in(file("utility"))
    .settings(commonSettings: _*)
    .settings(
      name := "cube-utility",
      libraryDependencies ++=
        Dependencies.googleCloudStorage ++
        Dependencies.firebase
    )

lazy val cubeCore =
  project
    .in(file("core"))
    .settings(commonSettings: _*)
    .settings(
      name := "cube-core",
      libraryDependencies ++=
        Dependencies.graph ++
        Dependencies.openNlp
    )

lazy val cubeAkka =
  project
    .in(file("akka"))
    .settings(commonSettings: _*)
    .settings(
      name := "cube-akka",
      libraryDependencies ++=
        Dependencies.akka
    )
    .dependsOn(cubeCore, utility)