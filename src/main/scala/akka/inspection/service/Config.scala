package akka.inspection.service

import akka.inspection.grpc
import scopt.{OParser, OParserBuilder}

import scala.concurrent.ExecutionContext
import scala.util.Success

case class Config(group: Option[String] = None)

object Bla {
  val builder: OParserBuilder[Config] = OParser.builder[Config]

  val parser = {
    import builder._

    OParser.sequence(
      programName("actor-inspector"),
      head("actor-inspector", "0.0.1"),
      opt[String]('g', "group")
        .action((g, c) => c.copy(group = Some(g)))
        .text("actors in the group")
    )
  }

  def bla(args: Array[String], client: grpc.ActorInspectionService)(implicit ec: ExecutionContext): Unit =
    OParser.parse(parser, args, Config()) match {
      case Some(Config(Some(group))) =>
        client.requestGroup(grpc.GroupRequest(group)).onComplete {
          case Success(grpc.GroupResponse(actors)) => println(actors)
          case other                               => println(other)
        }
      case _ => args.foreach(println)
    }
}
