package akka.inspection.client

import akka.inspection.ActorInspection.FragmentId
import akka.inspection.grpc.ActorInspectionService
import akka.inspection.manager.state.Group
import akka.inspection.manager.{FragmentIdsRequest, FragmentsRequest, GroupRequest, InspectableActorsRequest}

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object InteractiveMenus {
  final case object MainMenu extends InteractiveMenu {
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

  final case class InspectMenu(previous: InteractiveMenu) extends StackableInteractiveMenu {
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

  final case class FragmentIds(previous: InteractiveMenu) extends StackableInteractiveMenu {
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

  final case class Fragments(previous: InteractiveMenu) extends StackableInteractiveMenu {
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

  final case class ListMenu(previous: InteractiveMenu) extends StackableInteractiveMenu {
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

  final case class GroupMenu(previous: InteractiveMenu) extends StackableInteractiveMenu {
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

  final case class Waiting(previous: InteractiveMenu) extends StackableInteractiveMenu {
    override def apply(client: ActorInspectionService, input: Iterator[String]): Unit = {
      println("\nPress ENTER to continue...")
      read(input) { _ =>
        MainMenu(client, input)
      }
    }
  }

  final case class RetryWith(previous: InteractiveMenu) extends StackableInteractiveMenu {
    override def apply(client: ActorInspectionService, input: Iterator[String]): Unit = {
      println("Invalid option!")
      previous(client, input)
    }
  }
}
