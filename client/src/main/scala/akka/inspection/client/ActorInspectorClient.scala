package akka.inspection.client

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.inspection.ActorInspection.FragmentId
import akka.inspection.grpc
import akka.inspection.grpc.{ActorInspectionService, ActorInspectionServiceClient}
import akka.inspection.manager.state.Group
import akka.inspection.manager.{FragmentIdsRequest, FragmentsRequest, GroupRequest, InspectableActorsRequest}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object ActorInspectorClient {
  def main(args: Array[String]): Unit = {
    val conf = ConfigFactory.load()
//      .parseString("akka.http.server.preview.enable-http2 = on")
//      .withFallback(ConfigFactory.defaultApplication())

    implicit val sys: ActorSystem             = ActorSystem("CLIENT", conf)
    implicit val mat: ActorMaterializer       = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = sys.dispatcher

    val clientSettings = GrpcClientSettings
      .connectToServiceAt(conf.getString("akka.inspection.server.hostname"), conf.getInt("akka.inspection.server.port"))
      .withTls(false)

    val client: grpc.ActorInspectionService = ActorInspectionServiceClient(clientSettings)

    val a = scala.io.Source.stdin.getLines()
    MainMenu(client, a)

//    Service.execute(Array("--group", "hello"), client)
////    Service.execute(Array("--groups", "akka://HELLOWORLD/user/$a"), client)
////    Service.execute(Array("--fragment-ids", "akka://HELLOWORLD/user/$b"), client)
//    Service.execute(Array("--inspectable"), client)
//    Service.execute(Array("--fragment-ids", "akka://HELLOWORLD/user/stateless-actor-2"), client)
//    Service.execute(Array("--fragments", "akka://HELLOWORLD/user/stateless-actor-2", "a.b.*"), client)
  }

  sealed abstract class InitStage extends ((grpc.ActorInspectionService, Iterator[String]) => Unit) {
    override def apply(client: grpc.ActorInspectionService, input: Iterator[String]): Unit
  }

  sealed abstract class Stage extends InitStage {
    val previous: InitStage
  }

  final private case object MainMenu extends InitStage {
    override def apply(client: ActorInspectionService, input: Iterator[String]): Unit = {
      clearScreen()
      println("""
                |[1] List inspectable actors
                |[2] Inspect an actor
                |[3] Exit
              """.stripMargin)

      read(input) {
        case "1" => ListMenu(this)(client, input)
        case "2" => InspectMenu(this)(client, input)
        case "3" => System.exit(1)
        case _   => RetryWith(this)(client, input)
      }
    }
  }

  final private case class InspectMenu(previous: InitStage) extends Stage {
    override def apply(client: ActorInspectionService, input: Iterator[String]): Unit = {
      clearScreen()
      println("""
                |[1] List inspectable fragments
                |[2] Inspect fragments
                |[3] Back...
              """.stripMargin)

      read(input) {
        case "1" => FragmentIds(this)(client, input)
        case "2" => Fragments(this)(client, input)
        case "3" => previous(client, input)
        case _   => RetryWith(this)(client, input)
      }
    }
  }

  final private case class FragmentIds(previous: InitStage) extends Stage {
    override def apply(client: ActorInspectionService, input: Iterator[String]): Unit = {
      clearScreen()
      println("\nEnter an actor address: ")

      read(input) { actor =>
        client.requestFragmentIds(FragmentIdsRequest(actor).toGRPC).onComplete {
          case Success(res) =>
            println(res.toProtoString)
            Waiting(this)(client, input)

          case Failure(t) =>
            println(t.getMessage)
            Waiting(this)(client, input)
        }
      }
    }
  }

  final private case class Fragments(previous: InitStage) extends Stage {
    override def apply(client: ActorInspectionService, input: Iterator[String]): Unit = {
      clearScreen()
      println("\nEnter an actor address: ")

      read(input) { actor =>
        println("\nEnter the fragment-ids: ")
        read(input) { ids =>
          client.requestFragments(FragmentsRequest(split(ids).map(FragmentId), actor).toGRPC).onComplete {
            case Success(res) =>
              println(res.toProtoString)
              Waiting(this)(client, input)

            case Failure(t) =>
              println(t.getMessage)
              Waiting(this)(client, input)
          }
        }
      }
    }
  }

  final private case class ListMenu(previous: InitStage) extends Stage {
    override def apply(client: ActorInspectionService, input: Iterator[String]): Unit = {
      clearScreen()
      println("""
                |[1] All
                |[2] In group...
              """.stripMargin)

      read(input) {
        case "1" =>
          client.requestInspectableActors(InspectableActorsRequest.toGRPC).onComplete {
            case Success(res) =>
              println(res.toProtoString)
              Waiting(this)(client, input)

            case Failure(t) =>
              println(t.getMessage)
              Waiting(this)(client, input)
          }

        case "2" => GroupMenu(this)(client, input)
      }

    }
  }

  final private case class GroupMenu(previous: InitStage) extends Stage {
    override def apply(client: ActorInspectionService, input: Iterator[String]): Unit = {
      clearScreen()
      println("Group name...")

      read(input) { group =>
        client.requestGroup(GroupRequest(Group(group)).toGRPC).onComplete {
          case Success(res) =>
            println(res.toProtoString)
            Waiting(this)(client, input)

          case Failure(t) =>
            println(t.getMessage)
            Waiting(this)(client, input)
        }
      }
    }
  }

  final private case class Waiting(previous: InitStage) extends Stage {
    override def apply(client: ActorInspectionService, input: Iterator[String]): Unit = {
      println("\nPress ENTER to continue...")
      read(input) { _ =>
        MainMenu(client, input)
      }
    }
  }

  final private case class RetryWith(previous: InitStage) extends Stage {
    override def apply(client: ActorInspectionService, input: Iterator[String]): Unit = {
      println("Invalid option!")
      previous(client, input)
    }
  }

  private def clearScreen(): Unit = print("\u001b[2J")

  private def read(input: Iterator[String])(f: String => Unit): Unit =
    if (input.hasNext) {
      f(input.next())
    } else {
      println("NO INPUT")
    }

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  private def split(s: String): List[String] = {
    val p = "\"([^\"]*)\"|[^\"\\s]+".r

    p.findAllMatchIn(s)
      .map(
        m => if (m.group(1) != null) m.group(1) else m.group(0)
      )
      .toList
  }

}
