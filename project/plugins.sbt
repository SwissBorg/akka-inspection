resolvers += Resolver.bintrayRepo("kamon-io", "sbt-plugins")

addSbtPlugin("io.kamon"                % "sbt-aspectj-runner" % "1.1.0")
addSbtPlugin("io.spray"                % "sbt-revolver"       % "0.9.1")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc"      % "0.5.0")
