// / version := "0.1.0-SNAPSHOT"

//ThisBuild / scalaVersion := "3.3.7"

//lazy val root = (project in file("."))
//  .settings(
//    name := "fplab3"
//  )
name := "scala-fp-barbershop"
version := "1.0.0"
scalaVersion := "2.13.12"

scalacOptions ++= Seq(
  "-encoding", "utf-8",
  "-deprecation",
  "-feature"
)