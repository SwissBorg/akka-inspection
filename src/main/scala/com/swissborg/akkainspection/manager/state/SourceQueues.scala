package com.swissborg.akkainspection.manager.state

import com.swissborg.akkainspection.ActorInspection._
import com.swissborg.akkainspection.manager.ActorInspectorManager.InspectableActorRef
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}

import scala.concurrent.Future

/**
  * Manages the back-pressured queues to the inspectable actors.
  *
  * These queues are used to send requests to the actors.
  */
final private[manager] case class SourceQueues[M](
    private val sourceQueues: Map[InspectableActorRef, SourceQueueWithComplete[M]]
) {

  def add(ref: InspectableActorRef)(implicit mat: Materializer): SourceQueues[M] =
    copy(
      sourceQueues = sourceQueues + (ref -> Source
          .queue[M](5, OverflowStrategy.backpressure)
          .toMat(Sink.actorRefWithAck[M](ref.ref, Init, Ack, Complete))(Keep.left)
          .run())
    )

  def remove(ref: InspectableActorRef): SourceQueues[M] =
    copy(sourceQueues = sourceQueues - ref)

  /**
    * Offers `m` to the the actor `ref`.
    */
  def offer(m: M, ref: InspectableActorRef): Future[QueueOfferResult] =
    sourceQueues(ref).offer(m)
}

private[manager] object SourceQueues {

  def empty[M]: SourceQueues[M] =
    SourceQueues(Map.empty[InspectableActorRef, SourceQueueWithComplete[M]])
}
