package akka.inspection.manager.state

import akka.inspection.ActorInspection
import akka.inspection.ActorInspection.FragmentId
import akka.inspection.manager.ActorInspectorManager.InspectableActorRef
import akka.inspection.manager._
import akka.stream.{Materializer, QueueOfferResult}

import scala.collection.immutable.Queue
import scala.concurrent.Future

final private[manager] case class State(
  private val inspectableActors: InspectableActors,
  private val fragments: Fragments,
  private val groups: Groups,
  private val sourceQueues: SourceQueues[ActorInspection.ActorInspectionEvent],
  private val requestQueue: Queue[ResponseEvent]
)(implicit materializer: Materializer) {
  def put(ref: InspectableActorRef, keys: Set[FragmentId], groups: Set[Group]): State =
    copy(
      inspectableActors = inspectableActors.add(ref),
      fragments = this.fragments.addStateFragment(ref, keys),
      groups = this.groups.addGroups(ref, groups),
      sourceQueues = sourceQueues.add(ref)
    )

  def release(ref: InspectableActorRef): State =
    copy(
      inspectableActors = inspectableActors.remove(ref),
      fragments = fragments.remove(ref),
      groups = groups.remove(ref),
      sourceQueues = sourceQueues.remove(ref)
    )

  def offer(request: ActorInspection.ActorInspectionEvent,
            actor: String): Either[ActorNotInspectable, Future[QueueOfferResult]] =
    inspectableActors.fromId(actor).map(sourceQueues.offer(request, _))

  def inspectableActorIds: Set[InspectableActorRef] = inspectableActors.actorIds

  def groupsOf(id: String): Either[ActorNotInspectable, Set[Group]] =
    inspectableActors.fromId(id).map(groups.groupsOf)

  def inGroup(g: Group): Set[InspectableActorRef] = groups.inGroup(g)

  def fragmentIds(actor: String): Either[ActorNotInspectable, Set[FragmentId]] =
    inspectableActors.fromId(actor).map(fragments.stateFragmentsIds)
}

private[manager] object State {
  def empty(implicit materializer: Materializer): State =
    State(
      inspectableActors = InspectableActors.empty,
      fragments = Fragments.empty,
      groups = Groups.empty,
      sourceQueues = SourceQueues.empty[ActorInspection.ActorInspectionEvent],
      requestQueue = Queue.empty[ResponseEvent]
    )
}
