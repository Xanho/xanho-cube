import UdashBuild._

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
    .aggregate(utility, cubeCore, cubeAkka, webBackend, webFrontend, webSharedJVM, webSharedJS)

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
        Dependencies.akka ++
          Dependencies.akkaHttp
    )
    .dependsOn(cubeCore, utility)


//
// ScalaJS and UDash section
//

def crossLibs(configuration: Configuration) =
  libraryDependencies ++= Dependencies.crossDeps.value.map(_ % configuration)

lazy val webShared =
  crossProject
    .crossType(CrossType.Pure)
    .in(file("webShared"))
    .settings(commonSettings: _*)
    .settings(
      crossLibs(Provided)
    )

lazy val webSharedJVM =
  webShared.jvm

lazy val webSharedJS =
  webShared.js

lazy val webBackend =
  project
    .in(file("webBackend"))
    .settings(commonSettings: _*)
    .dependsOn(webSharedJVM, utility, cubeAkka)
    .settings(
      name := "web-backend",
      libraryDependencies ++= Dependencies.backendDeps.value,
      crossLibs(Compile),

      compile <<= (compile in Compile),
      (compile in Compile) <<= (compile in Compile).dependsOn(copyStatics),
      copyStatics := IO.copyDirectory((crossTarget in webFrontend).value / StaticFilesDir, (target in Compile).value / StaticFilesDir),
      copyStatics <<= copyStatics.dependsOn(compileStatics in webFrontend),

      mappings in(Compile, packageBin) ++= {
        copyStatics.value
        ((target in Compile).value / StaticFilesDir).***.get map { file =>
          file -> file.getAbsolutePath.stripPrefix((target in Compile).value.getAbsolutePath)
        }
      },

      watchSources ++= (sourceDirectory in webFrontend).value.***.get
    )

lazy val webFrontend =
  project
    .in(file("webFrontend"))
    .settings(commonSettings: _*)
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(webSharedJS)
    .settings(
      name := "web-frontend",
      libraryDependencies ++= Dependencies.frontendDeps.value,
      crossLibs(Compile),
      jsDependencies ++= Dependencies.frontendJSDeps.value,
      persistLauncher in Compile := true,

      compile <<= (compile in Compile).dependsOn(compileStatics),
      compileStatics := {
        IO.copyDirectory(sourceDirectory.value / "main/assets/fonts", crossTarget.value / StaticFilesDir / WebContent / "assets/fonts")
        IO.copyDirectory(sourceDirectory.value / "main/assets/images", crossTarget.value / StaticFilesDir / WebContent / "assets/images")
        val statics = compileStaticsForRelease.value
        (crossTarget.value / StaticFilesDir).***.get
      },

      artifactPath in(Compile, fastOptJS) :=
        (crossTarget in(Compile, fastOptJS)).value / StaticFilesDir / WebContent / "scripts" / "frontend-impl-fast.js",
      artifactPath in(Compile, fullOptJS) :=
        (crossTarget in(Compile, fullOptJS)).value / StaticFilesDir / WebContent / "scripts" / "frontend-impl.js",
      artifactPath in(Compile, packageJSDependencies) :=
        (crossTarget in(Compile, packageJSDependencies)).value / StaticFilesDir / WebContent / "scripts" / "frontend-deps-fast.js",
      artifactPath in(Compile, packageMinifiedJSDependencies) :=
        (crossTarget in(Compile, packageMinifiedJSDependencies)).value / StaticFilesDir / WebContent / "scripts" / "frontend-deps.js",
      artifactPath in(Compile, packageScalaJSLauncher) :=
        (crossTarget in(Compile, packageScalaJSLauncher)).value / StaticFilesDir / WebContent / "scripts" / "frontend-init.js"
    )
    .settings(
      bootSnippet := "org.xanho.web.frontend.Init().main();"
    )
