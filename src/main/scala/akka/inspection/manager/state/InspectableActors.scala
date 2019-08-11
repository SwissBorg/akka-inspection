package akka.inspection.manager.state

import akka.actor.ActorRef
import akka.inspection.manager.ActorInspectorManager.InspectableActorRef
import akka.inspection.manager._
import cats.implicits._

/**
 * Manages the inspectable actors.
 */
final private[manager] case class InspectableActors(private val actors: Set[InspectableActorRef]) extends AnyVal {
  def add(ref: InspectableActorRef): InspectableActors =
    copy(actors = actors + ref)

  def remove(ref: InspectableActorRef): InspectableActors = copy(actors = actors - ref)

  def actorRefs: Set[InspectableActorRef] = actors

  /**
   * Returns the [[InspectableActorRef]] for the `actor` if it exists.
   */
  def fromId(actor: String): Either[ActorNotInspectable, InspectableActorRef] =
    actors.find(_.toId === actor).toRight(ActorNotInspectable(actor))
}

private[manager] object InspectableActors {
  val empty: InspectableActors = InspectableActors(Set.empty)
}
