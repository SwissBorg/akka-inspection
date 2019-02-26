package akka.inspection

import akka.actor.{Actor, ActorRef}
import akka.inspection.ActorInspection.StateFragment.StateFragmentPartiallyApplied
import akka.inspection.ActorInspection._
import akka.inspection.ActorInspectorImpl.InspectableActorRef.{Ack, Init}
import akka.inspection.util.Render

/**
 * Adds the ability to inspect the actor's state to an external service. This trait is useful for actor's using
 * `context.become(...)` with a stateful-receive taking a state of type [[S]].
 *
 * It uses the concept of a [[StateFragment]] and it's related [[FragmentId]]. These represent a subset of
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
   * @see [[ActorInspection.FragmentId]]
   */
  type StateFragmentId = ActorInspection.FragmentId

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
    case r @ FragmentsRequest(_, replyTo, _) =>
      handleQuery(s, r, replyTo)
      sender() ! Ack

    case Init => sender() ! Ack // for the backpressured events from the `ActorInspectorManager`

  }

  protected def handleQuery(s: S, req: FragmentsRequest, replyTo: ActorRef): Unit =
    replyTo ! req.fragmentIds.foldLeft(FragmentsResponse(req.initiator)) {
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
   * @see [[StateFragment]]
   */
  final case class FragmentId(id: String) extends AnyVal

  /**
   * Describes how to extract a fragment from the state [[S]].
   * @tparam S the type of state.
   */
  sealed abstract class StateFragment[S] extends Product with Serializable {

    /**
     * Runs the [[StateFragment]] to build a [[FinalizedFragment]].
     */
    def run(s: S): FinalizedFragment
  }

  object StateFragment {

    /**
     * Describes a fragment whose value doesn't change.
     */
    final private case class Fix[S](fragment: String) extends StateFragment[S] {
      override def run(s: S): FinalizedFragment = RenderedFragment(fragment)
    }

    /**
     * Describes a fragment whose value is evaluated at each access.
     */
    final private case class Always[S](fragment: () => String) extends StateFragment[S] {
      override def run(s: S): FinalizedFragment = RenderedFragment(fragment())
    }

    /**
     * Describes a fragment dependent on [[S]].
     */
    final private case class Given[S](fragment: S => String) extends StateFragment[S] {
      def run(s: S): FinalizedFragment = RenderedFragment(fragment(s))
    }

    /**
     * Describes an undefined fragment.
     */
    final private case class Undefined[S]() extends StateFragment[S] {
      override def run(s: S): FinalizedFragment = UndefinedFragment
    }

    /**
     * Build a [[StateFragment]] independent of the state [[S]].
     */
    def apply[S, T: Render](t: T): StateFragment[S] = Fix(Render[T].render(t))

    /**
     * Build a [[StateFragment]] that's evaluated at each use
     * and independent of the state [[S]].
     */
    def always[S, T: Render](t: => T): StateFragment[S] = Always(() => Render[T].render(t))

    /**
     * Build a [[StateFragment]] from the state [[S]].
     */
    def state[S, T: Render](t: S => T): StateFragment[S] = Given(t.andThen(Render[T].render))

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
      def apply[T: Render](t: T): StateFragment[S] = StateFragment(t)
      def always[T: Render](t: => T): StateFragment[S] = StateFragment.always(t)
      def state[T: Render](t: S => T): StateFragment[S] = StateFragment.state(t)
      def fix(s: String): StateFragment[S] = StateFragment.fix(s)
      def sensitive: StateFragment[S] = StateFragment.sensitive
      def undefined: StateFragment[S] = StateFragment.undefined
    }
  }

  /**
   * A state fragment that has been run.
   *
   * @see [[StateFragment]]
   */
  sealed abstract class FinalizedFragment extends Product with Serializable
  final case class RenderedFragment(fragment: String) extends FinalizedFragment
  final case object UndefinedFragment extends FinalizedFragment
}
