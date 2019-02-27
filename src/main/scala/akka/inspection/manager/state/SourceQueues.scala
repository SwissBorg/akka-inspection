package akka.inspection.manager.state

import akka.inspection.ActorInspectorImpl.InspectableActorRef
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}

import scala.concurrent.Future

/**
 * Manages the
 */
final private[manager] case class SourceQueues[M](
  private val sourceQueues: Map[InspectableActorRef, SourceQueueWithComplete[M]]
) {
  def add(ref: InspectableActorRef)(implicit mat: Materializer): SourceQueues[M] =
    copy(
      sourceQueues = sourceQueues + (ref -> Source
        .queue[M](5, OverflowStrategy.backpressure)
        .toMat(
          Sink
            .actorRefWithAck(ref.ref, InspectableActorRef.Init, InspectableActorRef.Ack, InspectableActorRef.Complete)
        )(Keep.left)
        .run())
    )

  def remove(ref: InspectableActorRef): SourceQueues[M] = copy(sourceQueues = sourceQueues - ref)

  def offer(m: M, ref: InspectableActorRef): Future[QueueOfferResult] = sourceQueues(ref).offer(m)
}

private[manager] object SourceQueues {
  def empty[M]: SourceQueues[M] = SourceQueues(Map.empty)
}
