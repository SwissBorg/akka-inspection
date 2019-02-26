name := "akka-inspection"

version := "0.0.1"

scalaVersion := "2.12.6"
sbtVersion := "1.2.1"

scalacOptions += "-Ypartial-unification"

val akkaVersion = "2.5.21"
val catsVersion = "1.6.0"
val scalatestVersion = "3.0.5"
val monocleVersion = "1.5.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "org.typelevel" %% "cats-core" % catsVersion,
  "com.github.julien-truffaut" %% "monocle-core" % monocleVersion,
  "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion,
  "com.github.julien-truffaut" %% "monocle-law" % monocleVersion % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % scalatestVersion % Test
)

// GRPC
enablePlugins(AkkaGrpcPlugin)
enablePlugins(JavaAgent)
javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test"

lazy val root = (project in file("."))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
