package akka.inspection

import akka.actor.Actor
import akka.inspection.ActorInspection.FragmentId
import akka.inspection.inspectable.Inspectable
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
    override def receive: Receive = mainReceive(StatelessActor.State(0))

    def mainReceive(s: StatelessActor.State): Receive = withInspectionS("main")(s) {
      case _ => context.become(mainReceive(s.copy(i = s.i + 1)))
    }

    override val groups: Set[Group] = Set(Group("goodbye"), Group("universe"))

    implicit val stateInspectable: Inspectable[StatelessActor.State] = Inspectable.from(
      Map(
        FragmentId("yes") -> Fragment.state(_.i),
        FragmentId("no") -> Fragment.state(_.i + 1)
      )
    )
  }

  object StatelessActor {
    case class State(i: Int)
  }
}
