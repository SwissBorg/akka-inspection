resolvers += Resolver.bintrayRepo("kamon-io", "sbt-plugins")

addSbtPlugin("io.kamon"                % "sbt-aspectj-runner" % "1.1.0")
addSbtPlugin("io.spray"                % "sbt-revolver"       % "0.9.1")

// GRPC
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc"      % "0.5.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4") // ALPN agent
