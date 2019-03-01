package akka.inspection.manager

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, ORSet, ORSetKey, SelfUniqueAddress}
import cats.implicits._

import scala.concurrent.duration._

class BroadcastActor(manager: ActorRef) extends Actor with Stash {
  import BroadcastActor._

  private val replicator = DistributedData(context.system).replicator
  private val ManagersKey: ORSetKey[ActorRef] = ORSetKey[ActorRef]("broadcast")
  implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress
  replicator ! Subscribe(ManagersKey, self)
  replicator ! Update(ManagersKey, ORSet.empty[ActorRef], WriteAll(10 seconds))(_ :+ manager)
  replicator ! Get(ManagersKey, ReadAll(10 seconds))

  override def receive: Receive = awaitingManagers

  /**
   * Waits for the set of managers.
   */
  private def awaitingManagers: Receive = {
    case g @ GetSuccess(ManagersKey, _) =>
      unstashAll()
      context.become(receiveS(g.get(ManagersKey).elements, Map.empty))

    case GetFailure(ManagersKey, _) ⇒ throw new IllegalStateException("Woopsie.")
    case NotFound(ManagersKey, _) ⇒ throw new IllegalStateException("Woopsie.")

    case _: BroadcastRequest => stash()
  }

  /**
   * Handles the incoming events.
   *
   * @param managers the managers available in the cluster.
   * @param workList the responses awaiting answers from the managers.
   */
  private def receiveS(managers: Set[ActorRef], workList: Map[UUID, (Set[ActorRef], Option[ResponseEvent])]): Receive = {
    case e: BroadcastRequest =>
      /*
       We always send the request back to the manager that initiated the request.
       Even if it was forwarded because it does not know the potentially inspectable actor.
       By doing that, if it's the only available one, the broadcast actor pushes the problem
       of generating a failed response to the manager.
       */
      managers.foreach(_ ! e)
      context.become(receiveS(managers, workList + (e.id -> (managers, None))))

    case b @ BroadcastResponse(partialResponse, replyTo, id) =>
      val (waitingFor, maybeResponse) =
        workList.getOrElse(id, throw new IllegalStateException(s"'$id' should have been added to the worklist!"))

      val waitingFor0 = waitingFor - sender()

      // merges the responses. See the `Semigroup[ResponseEvent]` for the exact semantics.
      val maybeResponse0 = Some(maybeResponse.fold(partialResponse)(_ |+| partialResponse))

      // finished waiting for replies
      if (waitingFor0.isEmpty) {
        maybeResponse0.fold(throw new IllegalStateException("No response was generated even though a manager exists."))(
          response => replyTo ! response
        )
      } else context.become(receiveS(managers, workList + (id -> (waitingFor0, maybeResponse0))))

    case c @ Changed(ManagersKey) =>
      println(s"CHANGED ${c.get(ManagersKey).elements}")
      context.become(receiveS(c.get(ManagersKey).elements, workList))
  }
}

object BroadcastActor {

  /**
   *  Wrapper around [[RequestEvent]]s to signal that the request was sent from a manager's broadcaster.
   * @param request the wrapped request.
   * @param replyTo the actor that originated the request.
   * @param id the unique identifier of the request.
   */
  sealed abstract case class BroadcastRequest(request: RequestEvent, replyTo: ActorRef, id: UUID) {

    /**
     * @see [[BroadcastResponse.fromBroadcastedRequest()]]
     */
    def respondWith(response: ResponseEvent): BroadcastResponse =
      BroadcastResponse.fromBroadcastedRequest(this, response)
  }

  object BroadcastRequest {
    def apply(request: RequestEvent, replyTo: ActorRef): BroadcastRequest =
      new BroadcastRequest(request, replyTo, UUID.randomUUID()) {}
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

  def props(manager: ActorRef): Props = Props(new BroadcastActor(manager))
}
