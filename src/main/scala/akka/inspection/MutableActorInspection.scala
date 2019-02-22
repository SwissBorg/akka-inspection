package akka.inspection

import akka.actor.Actor
import cats.Show

trait MutableActorInspection extends ActorInspection[Unit] { _: Actor =>
  def responses: Map[Key, QueryResponse]

  override val showS: Show[Unit] = (_: Unit) => "()"

  override def responses(s: Unit): Map[Key, QueryResponse] = responses

  def inspectableReceive(r: Receive): Receive = inspectionReceive(()) orElse r
}
