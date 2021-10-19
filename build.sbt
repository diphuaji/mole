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
    libraryDependencies += scalaTest % Test,
    libraryDependencies ++= Seq(
      "com.jcraft" % "jsch" % "0.1.55"
    )
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
