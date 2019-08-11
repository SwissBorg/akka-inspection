package akka.inspection.manager.state

import akka.actor.ActorRef
import akka.inspection.ActorInspection
import akka.inspection.FragmentId
import akka.inspection.manager.ActorInspectorManager.InspectableActorRef
import akka.inspection.manager._
import akka.stream.{Materializer, QueueOfferResult}

import scala.collection.immutable.Queue
import scala.concurrent.Future

/**
 * The state of the `ActorInspectorManager`.
 */
final private[manager] case class State(
  private val inspectableActors: InspectableActors,
  private val groups: Groups,
  private val sourceQueues: SourceQueues[ActorInspection.Event]
)(implicit materializer: Materializer) {

  /**
   * Add `ref` to the inspectable actors.
   */
  def subscribe(ref: InspectableActorRef, groups: Set[Group]): State =
    copy(
      inspectableActors = inspectableActors.add(ref),
      groups = this.groups.addGroups(ref, groups),
      sourceQueues = sourceQueues.add(ref)
    )

  /**
   * Remove `ref` from the inspectable actors.
   */
  def unsubscribe(ref: InspectableActorRef): State =
    copy(
      inspectableActors = inspectableActors.remove(ref),
      groups = groups.remove(ref),
      sourceQueues = sourceQueues.remove(ref)
    )

  /** @see [[SourceQueues.offer()]] */
  def offer(request: ActorInspection.Event, actor: String): Either[ActorNotInspectable, Future[QueueOfferResult]] =
    inspectableActors.fromId(actor).map(sourceQueues.offer(request, _))

  def inspectableActorRefs: Set[InspectableActorRef] = inspectableActors.actorRefs

  /** @see [[Groups.groupsOf()]] */
  def groupsOf(actor: String): Either[ActorNotInspectable, Set[Group]] =
    inspectableActors.fromId(actor).map(groups.groupsOf)

  /** @see [[Groups.inGroup()]] */
  def inGroup(group: Group): Set[InspectableActorRef] = groups.inGroup(group)
}

private[manager] object State {
  def empty(implicit materializer: Materializer): State =
    State(
      inspectableActors = InspectableActors.empty,
      groups = Groups.empty,
      sourceQueues = SourceQueues.empty
    )
}
