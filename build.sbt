ThisBuild / organization := "com.example"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.8"

lazy val scalatraVersion = "3.1.0"
lazy val jettyVersion = "12.0.9"

lazy val root = (project in file("."))
  .settings(
    name := "scalatra-ping-api",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "3.7.0",
      "org.scalatra" %% "scalatra-jakarta" % scalatraVersion,
      "org.eclipse.jetty.ee10" % "jetty-ee10-servlet" % jettyVersion,
      "redis.clients" % "jedis" % "5.2.0",
      "ch.qos.logback" % "logback-classic" % "1.5.16" % Runtime,
      "org.scalatra" %% "scalatra-scalatest-jakarta" % scalatraVersion % Test
    ),
    Compile / run / mainClass := Some("com.example.api.Server"),
    Test / fork := true
  )
