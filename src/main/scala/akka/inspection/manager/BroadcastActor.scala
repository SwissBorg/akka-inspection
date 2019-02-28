package akka.inspection.manager

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.ddata.Replicator.{Get, GetSuccess, ReadMajority}
import akka.cluster.ddata.{DistributedData, ORSetKey}
import cats.implicits._

import scala.collection.immutable.Queue
import scala.concurrent.duration._

class BroadcastActor(replyTo: ActorRef, managersKey: String) extends Actor {
  import BroadcastActor._

  private val replicator = DistributedData(context.system).replicator
  private val ManagersKey: ORSetKey[ActorRef] = ORSetKey[ActorRef](managersKey)

  override def receive: Receive = waiting(Queue.empty, Map.empty)

  def waiting(requestsQueue: Queue[BroadcastedRequest],
              workList: Map[UUID, (Set[ActorRef], Option[ResponseEvent])]): Receive = {
    case e: RequestEvent =>
      replicator ! Get(ManagersKey, ReadMajority(timeout = 5.seconds))
      context.become(waiting(requestsQueue.enqueue(BroadcastedRequest(e)), workList))

    case BroadcastResponse(responseEvent, id) =>
      val (waitingFor, maybeResponse) = workList(id)

      val waitingFor0 = waitingFor - sender()
      val maybeResponse0 = maybeResponse.map(_ |+| responseEvent)

      // finished waiting for replies
      if (waitingFor0.isEmpty) maybeResponse0.foreach(replyTo ! _)
      else context.become(waiting(requestsQueue, workList + (id -> (waitingFor0, maybeResponse0))))

    case g @ GetSuccess(ManagersKey, _) =>
      val managers = g.get(ManagersKey).elements
      requestsQueue.dequeueOption.foreach {
        case (broadcastedRequest, queue0) =>
          managers.foreach(_ ! broadcastedRequest)
          context.become(waiting(queue0, workList + (broadcastedRequest.id -> (managers, None))))
      }
  }
}

object BroadcastActor {
  sealed abstract case class BroadcastedRequest(request: RequestEvent, id: UUID) {
    def response(response: ResponseEvent): BroadcastResponse = BroadcastResponse.fromBroadcastedRequest(this, response)
  }

  object BroadcastedRequest {
    def apply(request: RequestEvent): BroadcastedRequest = new BroadcastedRequest(request, UUID.randomUUID()) {}
  }

  final case class BroadcastResponse(response: ResponseEvent, id: UUID)

  object BroadcastResponse {
    def fromBroadcastedRequest(br: BroadcastedRequest, response: ResponseEvent): BroadcastResponse =
      BroadcastResponse(response, br.id)
  }

  def props(replyTo: ActorRef, managersKey: String): Props = Props(new BroadcastActor(replyTo, managersKey))
}
