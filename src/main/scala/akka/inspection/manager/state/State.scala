package akka.inspection.manager.state

import akka.inspection.ActorInspection.FragmentId
import akka.inspection.ActorInspectorImpl.InspectableActorRef
import akka.inspection.manager.ActorInspectorManager.ActorNotInspectable
import akka.stream.{Materializer, QueueOfferResult}

import scala.concurrent.{ExecutionContext, Future}

final private[manager] case class State[M](
  private val inspectableActors: InspectableActors,
  private val stateFragments: Fragments,
  private val groups: Groups,
  private val sourceQueues: SourceQueues[M]
)(implicit context: ExecutionContext, materializer: Materializer) {
  def put(ref: InspectableActorRef, keys: Set[FragmentId], groups: Set[Group]): State[M] =
    copy(
      inspectableActors = inspectableActors.add(ref),
      stateFragments = this.stateFragments.addStateFragment(ref, keys),
      groups = this.groups.addGroups(ref, groups),
      sourceQueues = sourceQueues.add(ref)
    )

  def release(ref: InspectableActorRef): State[M] =
    copy(
      inspectableActors = inspectableActors.remove(ref),
      stateFragments = stateFragments.remove(ref),
      groups = groups.remove(ref),
      sourceQueues = sourceQueues.remove(ref)
    )

  def offer(m: M, id: String)(
    implicit ec: ExecutionContext
  ): Either[ActorNotInspectable, Future[QueueOfferResult]] =
    inspectableActors.fromId(id).map(sourceQueues.offer(m, _))

  def inspectableActorIds: Set[String] = inspectableActors.actorIds

  def groups(id: String): Either[ActorNotInspectable, Set[Group]] =
    inspectableActors.fromId(id).map(groups.groups)

  def inGroup(g: Group): Set[InspectableActorRef] = groups.inGroup(g)

  def stateFragmentIds(id: String): Either[ActorNotInspectable, Set[FragmentId]] =
    inspectableActors.fromId(id).map(stateFragments.stateFragmentsIds)
}

private[manager] object State {
  def empty[M](implicit context: ExecutionContext, materializer: Materializer): State[M] =
    State(inspectableActors = InspectableActors.empty,
          stateFragments = Fragments.empty,
          groups = Groups.empty,
          sourceQueues = SourceQueues.empty)
}
