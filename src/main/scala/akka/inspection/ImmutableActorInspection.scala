package akka.inspection

import akka.actor.{Actor, ActorLogging}
import akka.inspection.ActorInspection._
import akka.inspection.inspectable.Inspectable

trait ImmutableActorInspection extends ActorInspection with ActorLogging { this: Actor =>

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
}
