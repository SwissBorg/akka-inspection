package akka.inspection

import akka.actor.{Actor, ActorRef}
import akka.inspection.ActorInspection._
import akka.inspection.ActorInspectorImpl.InspectableActorRef.{Ack, Init}

/**
 * Adds the ability to inspect the actor's state to an external service. This trait is useful for actor's using
 * `context.become(...)` with a stateful-receive taking a state of type [[S]].
 *
 * It uses the concept of a [[Fragment]] and it's related [[FragmentId]]. These represent a subset of
 * an actor's state. Note that multiple [[Fragment]]s can overlap.
 *
 * @tparam S the type of the stateful-receive's state.
 */
trait ActorInspection[S] extends Actor {
  import ActorInspection._

  /**
   * @see [[manager.state.Group]]
   */
  type Group = manager.state.Group

  /**
   * @see [[ActorInspection.FragmentId]]
   */
  type FragmentId = ActorInspection.FragmentId

  /**
   * @see [[akka.inspection.Fragmen]]
   */
  type Fragment = akka.inspection.Fragment[S]
  val Fragment = new akka.inspection.Fragment.FragmentPartiallyApplied[S]()

  /**
   * [[Fragment]]s given their id.
   */
  val fragments: Map[FragmentId, Fragment]

  /**
   * The groups in which the actor is a member.
   * This is used so that multiple actors can be inspected together.
   */
  val groups: Set[Group] = Set.empty

  /**
   * Adds the inspection events handling to `r`.
   */
  final def withInspection(s: S)(r: Receive): Receive = inspectionReceive(s).orElse(r)

  /**
   * A receive that handles inspection events.
   */
  final def inspectionReceive(s: S): Receive = {
    case r @ FragmentsRequest(_, replyTo, _) =>
      handleQuery(s, r, replyTo)
      sender() ! Ack

    case Init => sender() ! Ack // for the backpressured events from the `ActorInspectorManager`

  }

  protected def handleQuery(s: S, req: FragmentsRequest, replyTo: ActorRef): Unit =
    replyTo ! req.fragmentIds.foldLeft(FragmentsResponse(req.initiator)) {
      case (response, id) =>
        response.copy(
          fragments = response.fragments + (id -> fragments.getOrElse(id, Fragment.undefined).run(s))
        )
    }

  override def aroundPreStart(): Unit = {
    super.aroundPreStart()
    ActorInspector(context.system).put(self, fragments.keySet, groups)
  }

  override def aroundPostStop(): Unit = {
    ActorInspector(context.system).release(self)
    super.aroundPostStop()
  }
}

private[inspection] object ActorInspection {

  sealed abstract class FragmentEvent extends Product with Serializable {
    val initiator: ActorRef
  }

  final case class FragmentsRequest(fragmentIds: List[FragmentId], replyTo: ActorRef, initiator: ActorRef)
      extends FragmentEvent

  final case class FragmentsResponse(fragments: Map[FragmentId, FinalizedFragment], initiator: ActorRef)
      extends FragmentEvent

  object FragmentsResponse {
    def apply(initiator: ActorRef): FragmentsResponse = FragmentsResponse(Map.empty, initiator)
  }

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
}
