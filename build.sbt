name := "akka-inspection"

version := "0.0.1"

scalaVersion := "2.12.6"
sbtVersion := "1.2.1"

scalacOptions += "-Ypartial-unification"

val akkaVersion = "2.5.21"
val akkaHTTPVersion = "2.5.21"
val catsVersion = "1.6.0"
val scalatestVersion = "3.0.5"
val monocleVersion = "1.5.0"
val scoptVersion = "4.0.0-RC2"
val shapelessVersion = "2.3.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHTTPVersion,
  "org.typelevel" %% "cats-core" % catsVersion,
  "com.chuusai" %% "shapeless" % shapelessVersion,
  "com.github.julien-truffaut" %% "monocle-core" % monocleVersion,
  "com.github.scopt" %% "scopt" % scoptVersion,
  "com.github.julien-truffaut" %% "monocle-law" % monocleVersion % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test,
  "org.typelevel" %% "cats-testkit" % "1.1.0" % Test,
  "org.scalatest" %% "scalatest" % scalatestVersion % Test
)

// GRPC
enablePlugins(AkkaGrpcPlugin)
enablePlugins(JavaAgent)
javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test"

lazy val root = (project in file("."))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)

//parallelExecution in Test := false
