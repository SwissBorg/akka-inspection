package akka.inspection

import akka.actor.{Actor, ActorRef}
import akka.inspection.ActorInspectorImpl.InspectableActorRef.Ack
import akka.inspection.ActorInspectorManager.StateFragments.StateFragmentId
import cats.Show

trait ActorInspection[S] { _: Actor =>
  import ActorInspection._

  type Group = akka.inspection.ActorInspectorManager.Groups.Group
  type StateFragmentId = akka.inspection.ActorInspectorManager.StateFragments.StateFragmentId
  type StateFragment = akka.inspection.ActorInspection.StateFragment

  implicit def showS: Show[S]

  def responses(s: S): Map[StateFragmentId, StateFragment]

  def groups: Set[Group] = Set.empty

  private def allFragments(s: S): Map[StateFragmentId, StateFragment] =
    responses(s) + (StateFragmentId("all") -> StateFragment.now(s)) // TODO doesn't really work for var state

  def inspectableReceive(s: S)(r: Receive): Receive = inspectionReceive(s).orElse(r)

  protected def inspectionReceive(s: S): Receive = {
    case r: StateFragmentRequest =>
      handleQuery(s, r, sender())
      sender ! Ack
  }

  private def queryAll(s: S): StateFragmentResponse = ???

  protected def handleQuery(s: S, req: StateFragmentRequest, sender: ActorRef): Unit =
    sender ! req.fragmentIds.foldRight(StateFragmentResponse(req.initiator)) {
      case (id, response) =>
        response.copy(
          fragments = (id, allFragments(s).getOrElse(id, StateFragment.Undefined)) :: response.fragments
        )
    }
}

private[inspection] object ActorInspection {

  sealed abstract class Event extends Product with Serializable

  final case class StateFragmentRequest(fragmentIds: List[StateFragmentId], initiator: ActorRef) extends Event

  final case class StateFragmentResponse(fragments: List[(StateFragmentId, StateFragment)], initiator: ActorRef)
      extends Event
  object StateFragmentResponse {
    def apply(initiator: ActorRef): StateFragmentResponse = StateFragmentResponse(List.empty, initiator)
  }

  /**
   * Messages sent by an actor implementing [[ActorInspection]]
   * to the actor that made the related request.
   */
  sealed abstract class StateFragment extends Event
  object StateFragment {
    final case class Now(fragments: String) extends StateFragment
    final case class Lazy(res: () => Now) extends StateFragment
    final case object Undefined extends StateFragment

    def now[T: Show](t: T): Now = Now(Show[T].show(t))
    def later[T: Show](t: => T): Lazy = Lazy(() => now(t))
    def custom(s: String): Now = Now(s)
    val sensitive: Now = custom("[SENSITIVE]")
  }

  final case object ChildrenRequest
  final case class ChildrenResult(children: List[ActorRef])
}
