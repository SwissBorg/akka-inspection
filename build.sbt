import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

name := "akka-inspection"
organization := "com.swissborg"

scalaVersion := "2.12.8"
sbtVersion := "1.2.8"

val akkaVersion                = "2.5.23"
val akkaHTTPVersion            = "10.1.9"
val catsVersion                = "1.6.1"
val scalatestVersion           = "3.0.8"
val monocleVersion             = "1.6.0"
val scoptVersion               = "4.0.0-RC2"
val shapelessVersion           = "2.3.3"
val scalacheckShapelessVersion = "1.1.8"
val catsTestKitVersion         = "1.6.1"

//val akkaVersion                = "2.5.23"
//val akkaHTTPVersion            = "10.1.9"
//val catsVersion                = "1.6.1"
//val scalatestVersion           = "3.0.8"
//val monocleVersion             = "1.6.0"
//val scoptVersion               = "4.0.0-RC2"
//val shapelessVersion           = "2.3.3"
//val scalacheckShapelessVersion = "1.1.8"
//val catsTestKitVersion         = "1.6.1"

lazy val commonDependencies = Seq(
  libraryDependencies += "com.typesafe.akka"          %% "akka-actor"                % akkaVersion,
  libraryDependencies += "com.typesafe.akka"          %% "akka-cluster"              % akkaVersion,
  libraryDependencies += "com.typesafe.akka"          %% "akka-cluster-tools"        % akkaVersion,
  libraryDependencies += "com.typesafe.akka"          %% "akka-distributed-data"     % akkaVersion,
  libraryDependencies += "com.typesafe.akka"          %% "akka-stream"               % akkaVersion,
  libraryDependencies += "com.typesafe.akka"          %% "akka-http"                 % akkaHTTPVersion,
  libraryDependencies += "org.typelevel"              %% "cats-core"                 % catsVersion,
  libraryDependencies += "com.chuusai"                %% "shapeless"                 % shapelessVersion,
  libraryDependencies += "com.github.julien-truffaut" %% "monocle-core"              % monocleVersion,
  libraryDependencies += "com.github.scopt"           %% "scopt"                     % scoptVersion,
  libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging"             % "3.9.2",
  libraryDependencies += "com.github.julien-truffaut" %% "monocle-law"               % monocleVersion % Test,
  libraryDependencies += "com.typesafe.akka"          %% "akka-testkit"              % akkaVersion % Test,
  libraryDependencies += "com.typesafe.akka"          %% "akka-multi-node-testkit"   % akkaVersion % Test,
  libraryDependencies += "org.typelevel"              %% "cats-testkit"              % catsTestKitVersion % Test,
  libraryDependencies += "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % scalacheckShapelessVersion % Test,
  libraryDependencies += "org.scalatest"              %% "scalatest"                 % scalatestVersion % Test
)

lazy val commonScalacOptions = Seq(
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:experimental.macros",
  "-language:postfixOps",
  "-unchecked",
  "-Ywarn-dead-code",
  "-Ywarn-unused",
  "-Ywarn-unused:imports",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture",
  "-Yno-adapted-args"
)

lazy val commonSettings = Seq(
  scalacOptions ++= commonScalacOptions,
  parallelExecution in Test := false,
)

// GRPC
enablePlugins(AkkaGrpcPlugin)
enablePlugins(JavaAgent)
javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test"

sbVersionWithGit
commonSwissBorgSettings
sbMavenPublishSetting

scalafmtOnCompile := true

lazy val root = (project in file("."))
  .settings(commonDependencies)
  .settings(commonSettings)
  .enablePlugins(MultiJvmPlugin)
  .enablePlugins(AkkaGrpcPlugin)
  .enablePlugins(JavaAgent)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*)
