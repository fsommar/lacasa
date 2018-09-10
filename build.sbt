lazy val junitDependencies = Seq(
  "junit" % "junit" % "4.12" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test"
)

lazy val commonSettings = Defaults.coreDefaultSettings ++ Seq(
  scalaVersion := "2.11.8", // neg tests only work on 2.11 atm
  crossVersion := CrossVersion.full,
  version := "0.1.0-SNAPSHOT",
  organization := "io.github.phaller",
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.sonatypeRepo("releases"),
  publishMavenStyle := true,
  Test / publishArtifact := false,
  Test / parallelExecution := false,
  scalacOptions ++= Seq("-deprecation", "-feature"),
  logBuffered := false,
  libraryDependencies += "org.scala-lang.modules" %% "spores-core" % "0.2.4",
  libraryDependencies += { "org.scala-lang" % "scala-reflect" % scalaVersion.value },
  scalaHome := Option(System.getProperty("lacasa.scala.home")) map { scalaHome =>
    println(s"Using Scala home directory: $scalaHome")
    file(scalaHome)
  }
)

lazy val plugin = (project in file("plugin"))
  .dependsOn(core)
  .settings(
    name := "lacasa-plugin",
    commonSettings,
    Test / scalacOptions ++= {
      val jar: File = (Compile / Keys.`package`).value
      System.setProperty("lacasa.plugin.jar", jar.getAbsolutePath)
      val addPlugin = "-Xplugin:" + jar.getAbsolutePath
      val enablePlugin = "-P:lacasa:enable"
      val dummy = "-Jdummy=" + jar.lastModified
      Seq(addPlugin, enablePlugin, dummy)
    },
    Test / publishArtifact := false,
    Compile / resourceDirectory := baseDirectory.value / "src" / "main" / "scala" / "lacasa" / "embedded",
    libraryDependencies += { "org.scala-lang" % "scala-library"  % scalaVersion.value },
    libraryDependencies += { "org.scala-lang" % "scala-reflect"  % scalaVersion.value },
    libraryDependencies += { "org.scala-lang" % "scala-compiler" % scalaVersion.value },
    libraryDependencies ++= junitDependencies,
    publishMavenStyle := true,
    publishTo := publishLocation(version.value)
  )

lazy val core = (project in file("core"))
  .settings(
    name := "lacasa-core",
    commonSettings,
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-q", "-v", "-s"),
    Test / publishArtifact := false,
    libraryDependencies ++= junitDependencies,
    publishMavenStyle := true,
    publishTo := publishLocation(version.value)
  )

lazy val pluginSettings = Seq(
  Compile / scalacOptions ++= {
    val jar: File = (plugin / Compile / Keys.`package`).value
    System.setProperty("lacasa.plugin.jar", jar.getAbsolutePath)
    val addPlugin = "-Xplugin:" + jar.getAbsolutePath
    val enablePlugin = "-P:lacasa:enable"
    val dummy = "-Jdummy=" + jar.lastModified
    Seq(addPlugin, enablePlugin, dummy)
  }
)

lazy val sandbox = (project in file("sandbox"))
  .dependsOn(core)
  .settings (
    name := "sandbox",
    commonSettings,
    pluginSettings,
    libraryDependencies += { "org.scala-lang" % "scala-reflect" % scalaVersion.value },
    Compile / publishArtifact := false
  )

lazy val samples = (project in file("samples"))
  .dependsOn(core)
  .settings (
    name := "lacasa-samples",
    commonSettings,
    pluginSettings
  )

lazy val akka = (project in file("akka"))
  .dependsOn(core)
  .settings(
    name := "lacasa-akka",
    commonSettings,
    pluginSettings,
    scalacOptions += "-language:implicitConversions",
    libraryDependencies += { "org.scala-lang" % "scala-reflect" % scalaVersion.value },
    libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.14",
    libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.4.14"
  )

lazy val examples = (project in file("examples"))
.dependsOn(akka)
.settings (
  name := "lacasa-examples",
  commonSettings,
  pluginSettings
)

def publishLocation(version: String) = {
  val nexus = "https://oss.sonatype.org/"
  if (version.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}