package akka.inspection.manager.state

import akka.inspection.manager.ActorInspectorManager.InspectableActorRef
import akka.inspection.manager._

/**
 * Manages the inspectable actors.
 */
final private[manager] case class InspectableActors(private val actors: Set[InspectableActorRef]) extends AnyVal {
  def add(ref: InspectableActorRef): InspectableActors =
    copy(actors = actors + ref)

  def remove(ref: InspectableActorRef): InspectableActors = copy(actors = actors - ref)

  def actorRefs: Set[InspectableActorRef] = actors

  def actorIds: Set[String] = actors.map(_.toId)

  def fromId(s: String): Either[ActorNotInspectable, InspectableActorRef] =
    actors.find(_.toId == s).toRight(ActorNotInspectable(s))
}

private[manager] object InspectableActors {
  val empty: InspectableActors = InspectableActors(Set.empty)
}
