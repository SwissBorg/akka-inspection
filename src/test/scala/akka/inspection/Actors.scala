package akka.inspection

import akka.actor.Actor
import akka.inspection.ActorInspection.FragmentId
import akka.inspection.inspectable.{DerivedInspectable, Inspectable}
import akka.inspection.manager.state.Group

object Actors {
  class MutableActor extends Actor with MutableActorInspection {
    private var i: Int = 0

    override def receive: Receive = withInspection {
      case _ => i += 1
    }

    override val fragments: Map[FragmentId, Fragment] = Map(
      FragmentId("yes") -> Fragment.always(i),
      FragmentId("no") -> Fragment.always(i + 1)
    )

    override val groups: Set[Group] = Set(Group("hello"), Group("world"))
  }

  class StatelessActor extends Actor with ActorInspection {
    override def receive: Receive = mainReceive(StatelessActor.State(0, 1))

    def mainReceive(s: StatelessActor.State): Receive = withInspectionS("main")(s) {
      case _ => context.become(mainReceive(s.copy(yes = s.yes + 1, no = s.no + 1)))
    }

    override val groups: Set[Group] = Set(Group("goodbye"), Group("universe"))
    implicit val stateInspectable: Inspectable[StatelessActor.State] = DerivedInspectable.gen
  }

  object StatelessActor {
    case class State(yes: Int, no: Int)
  }
}
