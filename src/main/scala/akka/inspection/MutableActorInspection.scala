package akka.inspection

import akka.actor.{Actor, ActorLogging}

/**
 * Mix-in to make the actor available for inspection.
 */
trait MutableActorInspection extends ActorInspection[Unit] with ActorLogging { this: Actor =>
  final def withInspection(r: Receive): Receive = inspectionReceive(()).orElse(r)
  final def inspectionReceive(r: Receive): Receive = inspectionReceive(())
}
