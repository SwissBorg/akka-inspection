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
  private val stateFragments: Fragments,
  private val groups: Groups,
  private val sourceQueues: SourceQueues[ActorInspection.FragmentsRequest],
  private val requestQueue: Queue[ResponseEvent]
)(implicit materializer: Materializer) {
  def put(ref: InspectableActorRef, keys: Set[FragmentId], groups: Set[Group]): State =
    copy(
      inspectableActors = inspectableActors.add(ref),
      stateFragments = this.stateFragments.addStateFragment(ref, keys),
      groups = this.groups.addGroups(ref, groups),
      sourceQueues = sourceQueues.add(ref)
    )

  def release(ref: InspectableActorRef): State =
    copy(
      inspectableActors = inspectableActors.remove(ref),
      stateFragments = stateFragments.remove(ref),
      groups = groups.remove(ref),
      sourceQueues = sourceQueues.remove(ref)
    )

  def offer(request: ActorInspection.FragmentsRequest,
            actor: String): Either[ActorNotInspectable, Future[QueueOfferResult]] =
    inspectableActors.fromId(actor).map(sourceQueues.offer(request, _))

  def inspectableActorIds: Set[InspectableActorRef] = inspectableActors.actorIds

  def groups(id: String): Either[ActorNotInspectable, Set[Group]] =
    inspectableActors.fromId(id).map(groups.groups)

  def inGroup(g: Group): Set[InspectableActorRef] = groups.inGroup(g)

  def stateFragmentIds(id: String): Either[ActorNotInspectable, Set[FragmentId]] =
    inspectableActors.fromId(id).map(stateFragments.stateFragmentsIds)

  def stash(r: ResponseEvent): State = copy(requestQueue = requestQueue.enqueue(r))

  def optionUnstash: Option[(ResponseEvent, State)] = requestQueue.dequeueOption.map {
    case (e, q0) => (e, copy(requestQueue = q0))
  }
}

private[manager] object State {
  def empty(implicit materializer: Materializer): State =
    State(inspectableActors = InspectableActors.empty,
          stateFragments = Fragments.empty,
          groups = Groups.empty,
          sourceQueues = SourceQueues.empty,
          requestQueue = Queue.empty)
}
