name := "akka-inspection"

version := "0.0.1"

scalaVersion := "2.12.6"
sbtVersion := "1.2.1"

scalacOptions += "-Ypartial-unification"

lazy val akkaVersion      = "2.5.21"
lazy val catsVersion      = "1.6.0"
lazy val scalatestVersion = "3.0.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"         % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster"       % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream"        % akkaVersion,
  "org.typelevel"     %% "cats-core"          % catsVersion,
  "com.typesafe.akka" %% "akka-testkit"       % akkaVersion % Test,
  "org.scalatest"     %% "scalatest"          % scalatestVersion % Test
)

// GRPC
enablePlugins(AkkaGrpcPlugin)
enablePlugins(JavaAgent)
javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test"
