package akka.inspection

import java.util.UUID

import akka.actor.{Actor, ActorRef}
import akka.inspection
import akka.inspection.ActorInspection._
import akka.inspection.inspectable.Inspectable

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
  type Group = manager.state.Group

  type FragmentId = inspection.FragmentId
  def FragmentId(id: String) = inspection.FragmentId(id)

  /**
   * The groups of which the actor is a member.
   * This is used so that multiple actors can be inspected in one request.
   */
  val groups: Set[Group] = Set.empty

  final def bla[A](a: A, fragments: Map[FragmentId, Fragment[A]]): Receive = {
    case request: FragmentIdsRequest =>
      request.replyTo ! request.respondWith("default", fragments.keySet)
      sender() ! Ack

    case request: FragmentsRequest =>
      implicit val inspectableS = Inspectable.from(fragments)

      val fragmentIds = request.fragmentIds.foldLeft(Set.empty[FragmentId]) {
        case (fragmentIds, fragmentId) => fragmentIds ++ fragmentId.expand
      }

      val response = request.respondWith(
        "default",
        fragmentIds.foldLeft(Map.empty[FragmentId, FinalizedFragment]) {
          case (fragments0, id) =>
            fragments0 + (id -> inspectableS.fragments.getOrElse(id, Fragment.undefined).run(a))
        }
      )

      request.replyTo ! response

      sender() ! Ack

    case Init => sender() ! Ack
  }

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
