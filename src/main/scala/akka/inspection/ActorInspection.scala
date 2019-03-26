package akka.inspection

import java.util.UUID

import akka.actor.{Actor, ActorRef}
import akka.inspection.inspectable.Inspectable
import cats.implicits._

/**
 * Adds the ability to inspect the actor from outside the cluster.
 *
 * This trait is designed for actors whose state is transformed using `context.become(someReceive(state))`.
 * Use [[MutableActorInspection]] for actors whose state is transformed by mutating it.
 *
 * To do this the existent receive methods have to wrapped with `withInspectionS` or `inspectS` has to
 * be composed with the existing ones.
 * These methods rely on instance of the [[Inspectable]] typeclass. An instance can be automatically
 * derived for product type (i.e. case classes and tuples). Use [[akka.inspection.inspectable.DerivedInspectable.gen]]
 * to get the instance. If needed, it can also be manually be implemented.
 *
 */
trait ActorInspection extends Actor {
  import ActorInspection._

  type Group = manager.state.Group

  /**
   * The groups of which the actor is a member.
   * This is used so that multiple actors can be inspected in one request.
   */
  val groups: Set[Group] = Set.empty

  /**
   * Handles inspection requests.
   *
   * @param state the name given to the current state.
   * @param s the state.
   * @tparam S the type of the state.
   * @return a receive that handles inspection requests.
   */
  final def inspectS[S: Inspectable](state: String)(s: S): Receive = {
    case request: FragmentIdsRequest =>
      request.replyTo ! request.respondWith(state, Inspectable[S].fragments.keySet)
      sender() ! Ack

    case request: FragmentsRequest =>
      val inspectableS = Inspectable[S]

      val fragmentIds = request.fragmentIds.foldLeft(Set.empty[FragmentId]) {
        case (fragmentIds, fragmentId) => fragmentIds ++ fragmentId.expand
      }

      val response = request.respondWith(
        state,
        fragmentIds.foldLeft(Map.empty[FragmentId, FinalizedFragment]) {
          case (fragments, id) =>
            fragments + (id -> inspectableS.fragments.getOrElse(id, Fragment.undefined).run(s))
        }
      )

      request.replyTo ! response

      sender() ! Ack

    case Init => sender() ! Ack
  }

  /**
   * Adds handling of inspection requests to `r`.
   *
   * @param state the name given to the current state.
   * @param s the state.
   * @param receive the receive being wrapped.
   * @tparam S the type of the state
   * @return a receive adding the handling of inspection requests to `receive`.
   */
  final def withInspectionS[S: Inspectable](state: String)(s: S)(receive: Receive): Receive =
    inspectS(state)(s).orElse(receive)

  override def aroundPreStart(): Unit = {
    super.aroundPreStart()
    ActorInspector(context.system).subscribe(self, groups)
  }

  override def aroundPostStop(): Unit = {
    ActorInspector(context.system).unsubscribe(self)
    super.aroundPostStop()
  }
}

private[inspection] object ActorInspection {

  sealed abstract class Event extends Product with Serializable {
    val id: Option[UUID]
  }

  /**
   * A request of the current fragments.
   *
   * @param fragmentIds the fragments to inspect, if empty all fragments are inspected.
   * @param replyTo the actor expecting the result.
   * @param id the unique-id of the request/response
   */
  final case class FragmentsRequest(fragmentIds: List[FragmentId],
                                    replyTo: ActorRef,
                                    originalRequester: ActorRef,
                                    id: Option[UUID])
      extends Event {
    def respondWith(state: String, fragments: Map[FragmentId, FinalizedFragment]): FragmentsResponse =
      FragmentsResponse(state, fragments, originalRequester, id)
  }

  /**
   * Response to a [[FragmentsRequest]].
   *
   * @param state the state the actor is in.
   * @param fragments the fragments.
   * @param originalRequester the actor expecting the result.
   * @param id the unique-id of the request/response.
   */
  final case class FragmentsResponse(state: String,
                                     fragments: Map[FragmentId, FinalizedFragment],
                                     originalRequester: ActorRef,
                                     id: Option[UUID])
      extends Event

  final case class FragmentIdsRequest(replyTo: ActorRef, originalRequester: ActorRef, id: Option[UUID]) extends Event {
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
      extends Event

  /**
   * Represents the identifier of a subset of an actor's state.
   *
   * @see [[Fragment]]
   */
  final case class FragmentId(id: String) extends AnyVal {

    /**
     * Returns the expanded fragment-ids.
     *
     * Rules:
     *   - "*" expands to all the inspectable fragments
     *   - "a.b.*" expands to all the child fragments of "a.b"l
     *   - otherwise expands to itself
     */
    def expand[S](implicit inspectableS: Inspectable[S]): Set[FragmentId] =
      if (id.endsWith(".*") && !id.startsWith(".*")) {
        inspectableS.fragments.keySet.filter {
          case FragmentId(id) => id.startsWith(this.id.init)
        }
      } else if (id === "*") {
        inspectableS.fragments.keySet
      } else {
        inspectableS.fragments.keySet.filter(_.id === id)
      }

  }

  /**
   * A state fragment that has been run.
   *
   * @see [[Fragment]]
   */
  sealed abstract class FinalizedFragment             extends Product with Serializable
  final case class RenderedFragment(fragment: String) extends FinalizedFragment
  final case object UndefinedFragment                 extends FinalizedFragment

  sealed abstract class BackPressureSignal extends Product with Serializable
  final case object Init                   extends BackPressureSignal
  final case object Ack                    extends BackPressureSignal
  final case object Complete               extends BackPressureSignal
}
