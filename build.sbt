import Dependencies._
import sbt.Keys.{libraryDependencies, scalacOptions}

ThisBuild / scalaVersion := "2.13.6"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature")

fork := true

lazy val root = (project in file("."))
  .settings(
    name := "mole",
    //mainClass := Some("com.diphuaji.mole.Main"),
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "com.github.mwiede" % "jsch" % "0.1.69",
    libraryDependencies += "com.typesafe" % "config" % "1.4.1",
    libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.17.1",
    libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.17.1",
    libraryDependencies += "org.apache.logging.log4j" % "log4j-api-scala_2.13" % "12.0"
  )

root / assemblyMergeStrategy  := {
    case PathList("module-info.class") | PathList("Log4j2Plugins.dat")  => MergeStrategy.discard
    case x if x.endsWith("/module-info.class") | x.endsWith("/Log4j2Plugins.dat") => MergeStrategy.discard
    case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
}
// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
