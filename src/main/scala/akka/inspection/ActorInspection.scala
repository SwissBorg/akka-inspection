package akka.inspection

import java.util.UUID

import akka.actor.{Actor, ActorRef}
import akka.inspection.inspectable.Inspectable

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
    case request: FragmentIdsRequest =>
      request.replyTo ! request.respondWith(name, Inspectable[S].fragments.keySet)

    case request: FragmentsRequest =>
      request.replyTo ! request.respondWith(request.fragmentIds.foldLeft(Map.empty[FragmentId, FinalizedFragment]) {
        case (fragments, id) => fragments + (id -> Inspectable[S].fragments.getOrElse(id, Fragment.undefined).run(s))
      })

    case Init => sender() ! Ack
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
    val id: Option[UUID]
  }

  /**
   * A request of the current fragments.
   *
   * @param fragmentIds the fragments to inspect.
   * @param replyTo the actor expecting the result.
   * @param id
   */
  final case class FragmentsRequest(fragmentIds: List[FragmentId],
                                    replyTo: ActorRef,
                                    originalRequester: ActorRef,
                                    id: Option[UUID])
      extends FragmentEvent {
    def respondWith(fragments: Map[FragmentId, FinalizedFragment]): FragmentsResponse =
      FragmentsResponse(fragments, originalRequester, id)
  }

  /**
   * Response to a [[FragmentsRequest]].
   *
   * @param fragments the fragments.
   * @param originalRequester the actor expecting the result.
   * @param id the unique-id of the request/response.
   */
  final case class FragmentsResponse(fragments: Map[FragmentId, FinalizedFragment],
                                     originalRequester: ActorRef,
                                     id: Option[UUID])
      extends FragmentEvent

  /**
   *
   * @param replyTo
   * @param originalRequester
   * @param id
   */
  final case class FragmentIdsRequest(replyTo: ActorRef, originalRequester: ActorRef, id: Option[UUID])
      extends FragmentEvent {
    def respondWith(state: String, fragmentIds: Set[FragmentId]): FragmentIdsResponse =
      FragmentIdsResponse(state, fragmentIds, originalRequester, id)
  }

  /**
   * Response to a [[FragmentIdsRequest]].
   *
   * @param state the name of the state in which the actor is.
   * @param fragmentIds the fragment-ids inspectable in the current state.
   * @param originalRequester the actor expecting the result.
   * @param id the unique-id of the request/response.
   */
  final case class FragmentIdsResponse(state: String,
                                       fragmentIds: Set[FragmentId],
                                       originalRequester: ActorRef,
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
