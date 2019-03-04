package akka.inspection

import java.util.UUID

import akka.actor.{Actor, ActorRef}

// TODO DOC
/**
 * Adds the ability to inspect the actor's state to an external service. This trait is useful for actor's using
 * `context.become(...)` with a stateful-receive taking a state of type [[scalaz.Alpha.S]].
 *
 * It uses the concept of a [[Fragment]] and it's related [[akka.inspection.ActorInspection.FragmentId]]. These represent a subset of
 * an actor's state. Note that multiple [[Fragment]]s can overlap.
 */
trait ActorInspection extends Actor {
  import ActorInspection._

  /**
   * @see [[manager.state.Group]]
   */
  type Group = manager.state.Group

  /**
   * The groups in which the actor is a member.
   * This is used so that multiple actors can be inspected together.
   */
  val groups: Set[Group] = Set.empty

  def inspectS[S: Inspectable](name: String)(s: S): Receive = {
    val fragments0 = Inspectable[S].fragments(s)

    {
      case FragmentIdsRequest(replyTo, initiator, id) =>
        replyTo ! FragmentIdsResponse(name, fragments0.keySet, initiator, id)

      case FragmentsRequest(fragmentIds, replyTo, initiator, id) =>
        val response = fragmentIds.foldLeft(FragmentsResponse(initiator, id)) {
          case (response, id) =>
            response.copy(fragments = response.fragments + (id -> fragments0.getOrElse(id, Fragment.undefined).run(s))) // TODO still need to run? Only in the "mutable" case?
        }

        replyTo ! response

      case Init => sender() ! Ack
    }
  }

  /**
   * Adds the inspection events handling to `r`.
   */
  final def withInspectionS[S: Inspectable](name: String)(s: S)(r: Receive): Receive = inspectS(name)(s).orElse(r)

  override def aroundPreStart(): Unit = {
    super.aroundPreStart()
    ActorInspector(context.system).put(self, Set.empty, groups)
  }

  override def aroundPostStop(): Unit = {
    ActorInspector(context.system).release(self)
    super.aroundPostStop()
  }
}

private[inspection] object ActorInspection {

  sealed abstract class FragmentEvent extends Product with Serializable {
    val initiator: ActorRef
    val id: Option[UUID]
  }

  final case class FragmentsRequest(fragmentIds: List[FragmentId],
                                    replyTo: ActorRef,
                                    initiator: ActorRef,
                                    id: Option[UUID])
      extends FragmentEvent

  final case class FragmentsResponse(fragments: Map[FragmentId, FinalizedFragment],
                                     initiator: ActorRef,
                                     id: Option[UUID])
      extends FragmentEvent

  object FragmentsResponse {
    def apply(initiator: ActorRef, id: Option[UUID]): FragmentsResponse = FragmentsResponse(Map.empty, initiator, id)
  }

  final case class FragmentIdsRequest(replyTo: ActorRef, initiator: ActorRef, id: Option[UUID]) extends FragmentEvent

  final case class FragmentIdsResponse(state: String,
                                       fragmentIds: Set[FragmentId],
                                       initiator: ActorRef,
                                       id: Option[UUID])
      extends FragmentEvent

  /**
   * Represents the identifier of a subset of an actor's state.
   *
   * @see [[Fragment]]
   */
  final case class FragmentId(id: String) extends AnyVal

  /**
   * A state fragment that has been run.
   *
   * @see [[Fragment]]
   */
  sealed abstract class FinalizedFragment extends Product with Serializable
  final case class RenderedFragment(fragment: String) extends FinalizedFragment
  final case object UndefinedFragment extends FinalizedFragment

  sealed abstract class BackPressureSignal extends Product with Serializable
  final case object Init extends BackPressureSignal
  final case object Ack extends BackPressureSignal
  final case object Complete extends BackPressureSignal
}
