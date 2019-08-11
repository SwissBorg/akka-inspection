package akka.inspection

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.inspection
import akka.inspection.ActorInspection._
import akka.inspection.extension.ActorInspector

trait ActorInspection extends Actor with ActorLogging {
  type Group = manager.state.Group

  type FragmentId = inspection.FragmentId
  final def FragmentId(id: String) = inspection.FragmentId(id)

  /**
   * The groups of which the actor is a member.
   * This is used so that multiple actors can be inspected in one request.
   */
  val groups: Set[Group] = Set.empty

  /**
   * Returns a receive that handles inspection requests.
   *
   * @param state the name given to the current state.
   * @param s the current state
   * @tparam S the type of the state
   * @return a receive that handles inspection requests.
   */
  final protected def handleInspectionRequests[S](state: String,
                                                  s: S,
                                                  fragments: Map[FragmentId, Fragment[S]]): Receive = {
    case request: FragmentIdsRequest =>
      request.replyTo ! request.respondWith(state, fragments.keySet)
      sender() ! Ack

    case request: FragmentsRequest =>
      val fragmentIds = request.fragmentIds.foldLeft(Set.empty[FragmentId]) {
        case (fragmentIds, fragmentId) => fragmentIds ++ fragmentId.expand(fragments.keySet)
      }

      val response = request.respondWith(
        state,
        fragmentIds.foldLeft(Map.empty[FragmentId, FinalizedFragment]) {
          case (fragments0, id) =>
            fragments0 + (id -> fragments.getOrElse(id, Fragment.undefined).run(s))
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
