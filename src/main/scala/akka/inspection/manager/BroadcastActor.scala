package akka.inspection.manager

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, ORSetKey}
import cats.implicits._

import scala.concurrent.duration._

/**
 *
 * @param replyTo
 * @param managersKey
 */
class BroadcastActor(manager: ActorRef, managersKey: String) extends Actor with ActorLogging {
  import BroadcastActor._

  private val replicator = DistributedData(context.system).replicator
  private val ManagersKey: ORSetKey[ActorRef] = ORSetKey[ActorRef](managersKey)
  replicator ! Get(ManagersKey, ReadLocal)

  override def receive: Receive = awaitingManagers

  /**
   * Waits for the set of managers.
   */
  private def awaitingManagers: Receive = {
    case g @ GetSuccess(ManagersKey, _) => context.become(receiveS(g.get(ManagersKey).elements, Map.empty))
    case e: BroadcastRequest            => self ! e // cannot handle requests yet
  }

  /**
   * Handles the incoming events.
   * @param managers ???
   * @param workList the responses awaiting answers from the managers.
   */
  private def receiveS(managers: Set[ActorRef], workList: Map[UUID, (Set[ActorRef], Option[ResponseEvent])]): Receive = {
    case e: GroupedRequest =>
      managers.foreach(_ ! e)
      context.become(receiveS(managers, workList + (e.id -> (managers, None))))

    case e: ForwardRequest =>
      val otherManagers = managers - manager // we know the manager cannot respond successfully
      otherManagers.foreach(_ ! e)
      context.become(receiveS(otherManagers, workList + (e.id -> (managers, None))))

    case BroadcastResponse(responseEvent, replyTo, id) =>
      val (waitingFor, maybeResponse) =
        workList.getOrElse(id, throw new IllegalStateException(s"'$id' should have been added to the worklist!"))

      log.debug(s"Waiting for: $waitingFor\n Response: $maybeResponse")

      val waitingFor0 = waitingFor - sender()
      val maybeResponse0 = Some(maybeResponse.fold(responseEvent)(_ |+| responseEvent)) // TODO seems weird

      log.debug(s"UPDATED: Waiting for: $waitingFor0\n Response: $maybeResponse0")

      // finished waiting for replies
      if (waitingFor0.isEmpty) {
        maybeResponse0.fold(throw new IllegalStateException("No response was generated even though a manager exists.")) {
          response =>
            log.debug(s"Sending response: $response")
            replyTo ! response
        }
      } else context.become(receiveS(managers, workList + (id -> (waitingFor0, maybeResponse0))))

    case c @ Changed(ManagersKey) =>
      log.debug(c.get(ManagersKey).toString)
      context.become(receiveS(c.get(ManagersKey).elements, workList))
  }
}

object BroadcastActor {

  /**
   * Wrapper around [[RequestEvent]]s to signal that the request was sent from a manager's broadcaster.
   */
  sealed abstract class BroadcastRequest extends Product with Serializable {
    val request: RequestEvent
    val replyTo: ActorRef
    val id: UUID

    /**
     * @see [[BroadcastResponse.fromBroadcastedRequest()]]
     */
    def respondWith(response: ResponseEvent): BroadcastResponse =
      BroadcastResponse.fromBroadcastedRequest(this, response)
  }

  /**
   * Request that has to be forwarded to another manager.
   *
   * @see [[BroadcastRequest]]
   */
  sealed abstract case class ForwardRequest(request: RequestEvent, replyTo: ActorRef, id: UUID) extends BroadcastRequest

  object ForwardRequest {
    def apply(request: RequestEvent, replyTo: ActorRef): ForwardRequest =
      new ForwardRequest(request, replyTo, UUID.randomUUID()) {}
  }

  /**
   * Request that needs the answer of all the managers in order to be responded to.
   *
   * @see [[BroadcastRequest]]
   */
  sealed abstract case class GroupedRequest(request: RequestEvent, replyTo: ActorRef, id: UUID) extends BroadcastRequest

  object GroupedRequest {
    def apply(request: RequestEvent, replyTo: ActorRef): GroupedRequest =
      new GroupedRequest(request, replyTo, UUID.randomUUID()) {}

  }

  /**
   * Wrapper around [[ResponseEvent]] to signal that the [[response]] is an answer to a [[BroadcastRequest]].
   *
   * @param response the wrapped response.
   * @param id the unique identifier of the response. Must be the same as the request it responds to!
   */
  final case class BroadcastResponse(response: ResponseEvent, replyTo: ActorRef, id: UUID)

  object BroadcastResponse {

    /**
     * Helper to build a [[BroadcastResponse]] from a [[BroadcastRequest]] that inherits its id.
     */
    def fromBroadcastedRequest(br: BroadcastRequest, response: ResponseEvent): BroadcastResponse =
      BroadcastResponse(response, br.replyTo, br.id)
  }

  def props(manager: ActorRef, managersKey: String): Props = Props(new BroadcastActor(manager, managersKey))
}
