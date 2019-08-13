package akka.inspection

import akka.actor.Actor
import akka.inspection.Actors.StatelessActor.InnerState
import akka.inspection.inspectable.Inspectable
import akka.inspection.inspectable.derivation.DerivedInspectable
import akka.inspection.manager.state.Group

object Actors {

  class MutableActor extends Actor with MutableInspection {
    private var i: Int = 0

    override def receive: Receive = {
      case _ => i += 1
    }

    override val fragments: Map[FragmentId, Fragment] = Map(
      FragmentId("yes") -> Fragment.always(i),
      FragmentId("no")  -> Fragment.always(i + 1)
    )

    override val groups: Set[Group] = Set(Group("hello"), Group("world"))
  }

  class StatelessActor extends Actor with ImmutableInspection {
    override def receive: Receive = mainReceive(StatelessActor.State(0, 1, InnerState(2, 3)))

    def mainReceive(s: StatelessActor.State): Receive = withInspection("main")(s) {
      case _ =>
        context.become(
          mainReceive(
            s.copy(
              yes = s.yes + 1,
              no = s.no + 1,
              maybe = s.maybe.copy(maybeYes = s.maybe.maybeYes + 1, maybeNo = s.maybe.maybeNo + 1)
            )
          )
        )
    }

    override val groups: Set[Group]                                  = Set(Group("goodbye"), Group("universe"))
    implicit val stateInspectable: Inspectable[StatelessActor.State] = DerivedInspectable.gen
  }

  object StatelessActor {
    final case class State(yes: Int, no: Int, maybe: InnerState)
    final case class InnerState(maybeYes: Int, maybeNo: Int)
  }
}
