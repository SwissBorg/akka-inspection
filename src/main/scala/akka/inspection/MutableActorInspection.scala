package akka.inspection

import akka.actor.{Actor, ActorLogging}
import cats.Show

/**
 * Mix-in to make the actor available for inspection.
 */
trait MutableActorInspection extends ActorInspection[Unit] with ActorLogging { this: Actor =>
  def responses: Map[StateFragmentId, StateFragment]

  override val showS: Show[Unit] = (_: Unit) => "()"

  override def responses(s: Unit): Map[StateFragmentId, StateFragment] = responses

  def inspectableReceive(r: Receive): Receive = inspectionReceive(()).orElse(r)
}
