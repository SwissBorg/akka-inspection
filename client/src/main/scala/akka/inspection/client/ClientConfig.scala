package akka.inspection.client

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import scopt.{OParser, OParserBuilder}
import com.typesafe.config.{Config => TSConfig}

object ClientConfig {
  final case class Config(hostname: Option[String], port: Option[Int])

  object Config {
    val empty: Config = Config(None, None)
  }

  private val builder: OParserBuilder[Config] = OParser.builder[Config]

  private val parser: OParser[Unit, Config] = {
    import builder._

    OParser.sequence(
      programName("actor-inspector"),
      head("actor-inspector", "0.0.1"),
      opt[String]('h', "hostname")
        .action((hostname, config) => config.copy(hostname = Some(hostname)))
        .text("The hostname to connect to."),
      opt[Int]('p', "port").action((port, config) => config.copy(port = Some(port))).text("The port to connect to."),
    )
  }

  def parse(args: Array[String], conf: TSConfig)(implicit system: ActorSystem): GrpcClientSettings =
    OParser.parse(parser, args, Config.empty) match {
      case Some(config) =>
        config match {
          case Config(Some(hostname), Some(port)) => clientSetting(hostname, port)
          case Config(Some(hostname), None)       => clientSetting(hostname, conf.getInt("akka.inspection.server.port"))
          case Config(None, Some(port))           => clientSetting(conf.getString("akka.inspection.server.hostname"), port)
          case Config(None, None) =>
            clientSetting(conf.getString("akka.inspection.server.hostname"), conf.getInt("akka.inspection.server.port"))
        }

      case None => ???
    }

  private def clientSetting(hostname: String, port: Int)(implicit system: ActorSystem): GrpcClientSettings =
    GrpcClientSettings
      .connectToServiceAt(hostname, port)
      .withTls(false)
}
