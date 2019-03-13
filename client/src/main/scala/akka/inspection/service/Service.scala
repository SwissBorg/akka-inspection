package akka.inspection.service

import akka.inspection.ActorInspection.FragmentId
import akka.inspection.grpc
import akka.inspection.manager._
import akka.inspection.manager.state.Group
import scopt.{OParser, OParserBuilder}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object Service {
  sealed abstract class Command                                          extends Product with Serializable
  final case class GroupCmd(name: String)                                extends Command
  final case class GroupsCmd(actor: String)                              extends Command
  final case class FragmentIdsCmd(actor: String)                         extends Command
  final case class FragmentsCmd(actor: String, ids: Option[Seq[String]]) extends Command
  final case object InspectableCmd                                       extends Command
  final case object Init                                                 extends Command
  final case object Error                                                extends Command

  private val builder: OParserBuilder[Command] = OParser.builder[Command]

  private val parser: OParser[Unit, Command] = {
    import builder._

    OParser.sequence(
      programName("actor-inspector"),
      head("actor-inspector", "0.0.1"),
      opt[Unit]("inspectable").action((_, _) => InspectableCmd).text("inspectable actors"),
      opt[String]("group").action((name, _) => GroupCmd(name)).text("actors in the group"),
      opt[String]("groups").action((actor, _) => GroupsCmd(actor)).text("groups of the actor"),
      opt[String]("fragment-ids").action((actor, _) => FragmentIdsCmd(actor)).text("fragment-ids of the actor"),
      opt[String]("fragments")
        .action((fs, _) => FragmentsCmd(fs, None))
        .text("fragments of the actor to inspect")
        .children {
          OParser.sequence(arg[String]("<fragment-ids>...").unbounded().action {
            case (fid, FragmentsCmd(actor, fids)) => FragmentsCmd(actor, Some(fids.fold(Seq(fid))(_ :+ fid)))
            case _                                => Error
          })
        },
    )
  }

  def execute(args: Array[String], client: grpc.ActorInspectionService)(implicit ec: ExecutionContext): Unit =
    OParser.parse(parser, args, Init) match {
      case Some(value) =>
        value match {
          case GroupCmd(name) =>
            client.requestGroup(GroupRequest(Group(name)).toGRPC).onComplete {
              case Success(res) => println(res.toProtoString)
              case Failure(t)   => println(t.getMessage)
            }

          case GroupsCmd(actor) =>
            client.requestGroups(GroupsRequest(actor).toGRPC).onComplete {
              case Success(res) => println(res.toProtoString)
              case Failure(t)   => println(t.getMessage)
            }

          case FragmentIdsCmd(actor) =>
            client.requestFragmentIds(FragmentIdsRequest(actor).toGRPC).onComplete {
              case Success(res) => println(res.toProtoString)
              case Failure(t)   => println(t.getMessage)
            }

          case FragmentsCmd(actor, Some(ids)) =>
            client.requestFragments(FragmentsRequest(ids.map(FragmentId).toList, actor).toGRPC).onComplete {
              case Success(res) => println(res.toProtoString)
              case Failure(t)   => println(t.getMessage)
            }

          case InspectableCmd =>
            client.requestInspectableActors(InspectableActorsRequest.toGRPC).onComplete {
              case Success(res) => println(res.toProtoString)
              case Failure(t)   => println(t.getMessage)
            }

          case Init => println("Provide some arguments...")
          case _    => println("Invalid arguments...")
        }
      case None => println("Error...")
    }
}
