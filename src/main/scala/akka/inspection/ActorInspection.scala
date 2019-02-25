package akka.inspection

import akka.actor.{Actor, ActorRef}
import akka.inspection.ActorInspection.StateFragment.StateFragmentPartiallyApplied
import akka.inspection.ActorInspection._
import akka.inspection.ActorInspectorImpl.InspectableActorRef.{Ack, Init}
import cats.Show

/**
 * Adds the ability to inspect the actor's state to an external service. This trait is useful for actor's using
 * `context.become(...)` with a stateful-receive taking a state of type [[S]].
 *
 * It uses the concept of a [[StateFragment]] and it's related [[StateFragmentId]]. These represent a subset of
 * an actor's state. Note that multiple [[StateFragment]]s can overlap.
 *
 * @tparam S the type of the stateful-receive's state.
 */
trait ActorInspection[S] extends Actor {
  import ActorInspection._

  /**
   * @see [[ActorInspectorManager.Groups.Group]]
   */
  type Group = ActorInspectorManager.Groups.Group

  /**
   * @see [[ActorInspection.StateFragmentId]]
   */
  type StateFragmentId = ActorInspection.StateFragmentId

  /**
   * @see [[ActorInspection.StateFragment]]
   */
  type StateFragment = ActorInspection.StateFragment[S]
  val StateFragment = new StateFragmentPartiallyApplied[S]()

  /**
   * [[StateFragment]]s given their id.
   */
  def stateFragments: Map[StateFragmentId, StateFragment]

  /**
   * The groups in which the actor is a member.
   * This is used so that multiple actors can be inspected together.
   */
  def groups: Set[Group] = Set.empty

  /**
   * Adds the inspection events handling to `r`.
   */
  final def withInspection(s: S)(r: Receive): Receive = inspectionReceive(s).orElse(r)

  /**
   * A receive that handles inspection events.
   */
  final def inspectionReceive(s: S): Receive = {
    case r @ StateFragmentRequest(_, replyTo, _) =>
      handleQuery(s, r, replyTo)
      sender() ! Ack

    case Init => sender() ! Ack // for the backpressured events from the `ActorInspectorManager`

  }

  protected def handleQuery(s: S, req: StateFragmentRequest, replyTo: ActorRef): Unit =
    replyTo ! req.fragmentIds.foldLeft(StateFragmentResponse(req.initiator)) {
      case (response, id) =>
        response.copy(
          fragments = response.fragments + (id -> stateFragments.getOrElse(id, StateFragment.undefined).run(s))
        )
    }

  override def aroundPreStart(): Unit = {
    super.aroundPreStart()
    ActorInspector(context.system).put(self, stateFragments.keySet, groups)
  }

  override def aroundPostStop(): Unit = {
    ActorInspector(context.system).release(self)
    super.aroundPostStop()
  }
}

private[inspection] object ActorInspection {

  sealed abstract class StateFragmentEvent extends Product with Serializable {
    val initiator: ActorRef
  }

  final case class StateFragmentRequest(fragmentIds: Set[StateFragmentId], replyTo: ActorRef, initiator: ActorRef)
      extends StateFragmentEvent

  final case class StateFragmentResponse(fragments: Map[StateFragmentId, FinalizedStateFragment0], initiator: ActorRef)
      extends StateFragmentEvent

  object StateFragmentResponse {
    def apply(initiator: ActorRef): StateFragmentResponse = StateFragmentResponse(Map.empty, initiator)
  }

  /**
   * Represents the identifier of a subset of an actor's state.
   * @see [[StateFragment]]
   */
  final case class StateFragmentId(id: String) extends AnyVal

  /**
   * Describes how to extract a fragment from the state [[S]].
   * @tparam S the type of state.
   */
  sealed abstract class StateFragment[S] extends Product with Serializable {

    /**
     * Runs the [[StateFragment]] to build a [[FinalizedStateFragment0]].
     */
    def run(s: S): FinalizedStateFragment0
  }

  object StateFragment {

    /**
     * Describes a fragment whose value doesn't change.
     */
    final private case class Fix[S](fragment: String) extends StateFragment[S] {
      override def run(s: S): FinalizedStateFragment0 = FinalizedStateFragment(fragment)
    }

//    final private case class Later[S](fragment: () => String) extends StateFragment[S] {}

      /**
     * Describes a fragment dependent on [[S]].
     */
    final private case class Given[S](fragment: S => String) extends StateFragment[S] {
      def run(s: S): FinalizedStateFragment0 = FinalizedStateFragment(fragment(s))
    }

    /**
     * Describes an undefined fragment.
     */
    final private case class Undefined[S]() extends StateFragment[S] {
      override def run(s: S): FinalizedStateFragment0 = UndefinedFragment
    }

    /**
     * Build a [[StateFragment]] independent of the state [[S]].
     */
    def apply[S, T: Show](t: T): StateFragment[S] = Fix(Show[T].show(t))

    /**
     * Build a [[StateFragment]] from the state [[S]].
     */
    def state[S, T: Show](t: S => T): StateFragment[S] = Given(t.andThen(Show[T].show))

    /**
     * Build a [[StateFragment]] always returning `s`.
     */
    def fix[S](s: String): StateFragment[S] = Fix(s)

    /**
     * Build a [[StateFragment]] that hides sensitive data.
     */
    def sensitive[S]: StateFragment[S] = fix("[SENSITIVE]")

    /**
     * [[StateFragment]] fallback.
     */
    private[inspection] def undefined[S]: StateFragment[S] = Undefined()

    /**
     * Helper to provide [[S]] so that the user that extends [[ActorInspection]]
     * does not have to annotate the state's type when building state fragments.
     */
    final private[inspection] class StateFragmentPartiallyApplied[S](val dummy: Boolean = true) extends AnyVal {
      def apply[T: Show](t: T): StateFragment[S] = StateFragment(t)
      def state[T: Show](t: S => T): StateFragment[S] = StateFragment.state(t)
      def fix(s: String): StateFragment[Any] = StateFragment.fix(s)
      def sensitive: StateFragment[S] = StateFragment.sensitive
      def undefined: StateFragment[S] = StateFragment.undefined
    }
  }

  /**
   * A state fragment that has been run.
   *
   * @see [[StateFragment]]
   */
  sealed abstract class FinalizedStateFragment0 extends Product with Serializable
  final case class FinalizedStateFragment(fragment: String) extends FinalizedStateFragment0
  final case object UndefinedFragment extends FinalizedStateFragment0
}
