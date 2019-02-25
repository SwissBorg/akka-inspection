package akka.inspection

import akka.actor.{Actor, ActorLogging}
import cats.Show

/**
 * Mix-in to make the actor available for inspection.
 */
trait MutableActorInspection extends ActorInspection[Unit] with ActorLogging { this: Actor =>
  override val showS: Show[Unit] = (_: Unit) => "()"

  def inspectableReceive(r: Receive): Receive = inspectionReceive(()).orElse(r)
}
