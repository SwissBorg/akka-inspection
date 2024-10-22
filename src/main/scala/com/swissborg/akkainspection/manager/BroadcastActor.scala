package com.swissborg.akkainspection.manager

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash, Terminated}
import akka.cluster.ddata.Replicator.{Subscribe => DDataSubscribe, _}
import akka.cluster.ddata.{DistributedData, ORSet, ORSetKey, SelfUniqueAddress}
import cats.implicits._

import scala.concurrent.duration._

/**
  * An actor that broadcasts requests to `ActorInspectorManagers` in other nodes.
  *
  * Each `ActorInspectorManagers` has such an actor as a children.
  */
class BroadcastActor(manager: ActorRef) extends Actor with Stash with ActorLogging {
  import BroadcastActor._

  private val replicator                      = DistributedData(context.system).replicator
  private val ManagersKey: ORSetKey[ActorRef] = ORSetKey[ActorRef]("broadcast")
  implicit val node: SelfUniqueAddress        = DistributedData(context.system).selfUniqueAddress
  replicator ! DDataSubscribe(ManagersKey, self)
  replicator ! Update(ManagersKey, ORSet.empty[ActorRef], WriteAll(10.seconds))(_ :+ manager)
  replicator ! Get(ManagersKey, ReadAll(10.seconds))

  override def receive: Receive = awaitingManagers

  /**
    * Waits for the set of managers.
    */
  private def awaitingManagers: Receive = {
    case g @ GetSuccess(ManagersKey, _) =>
      val managers = g.get(ManagersKey).elements
      watch(managers)
      context.become(receiveS(managers, Map.empty))
      unstashAll()

    case GetFailure(ManagersKey, _) ⇒
      throw new Exception("Could not get the ddata key.")
    case NotFound(ManagersKey, _) ⇒ throw new Exception("Ddata key not found.")

    case _: BroadcastRequest =>
      // Stash requests while waiting for the refs of managers in the other nodes.
      stash()
  }

  /**
    * Handles the incoming events.
    *
    * @param managers the managers available in the cluster.
    * @param workList the responses awaiting answers from the managers.
    */
  private def receiveS(managers: Set[ActorRef], workList: Map[UUID, Work]): Receive = {
    case broadcastRequest @ BroadcastRequest(_, initResponse, replyTo, id) =>
      val otherManagers = managers - manager

      if (otherManagers.isEmpty) {
        // no need to broadcast
        replyTo ! initResponse
      } else {
        // broadcast request and add it to the worklist
        otherManagers.foreach(_ ! broadcastRequest.copy(replyTo = self))
        context.become(
          receiveS(managers, workList + (id -> Work(otherManagers, replyTo, initResponse)))
        )
      }

    case r @ BroadcastResponse(partialResponse, id) =>
      workList
        .get(id)
        .orElse(Some(log.warning(s"Received a response with an unknown id: $r")))
        .foreach {
          case Work(waitingFor, replyTo, response) =>
            val waitingFor0 = waitingFor - sender()

            // merges the responses. See the `Semigroup[ResponseEvent]` for the exact semantics.
            val response0 = response |+| partialResponse

            // finished waiting for replies
            if (waitingFor0.isEmpty) {
              replyTo ! response0
            } else
              context.become(receiveS(managers, workList + (id -> Work(waitingFor0, replyTo, response0))))
        }

    case c @ Changed(ManagersKey) =>
      val newManagers = c.get(ManagersKey).elements
      watch(newManagers -- managers) // track the termination of managers

      val removedManagers = managers -- newManagers
      context.become(receiveS(newManagers, removedManagers.foldLeft(workList)(update)))

    case Terminated(terminatedManager) =>
      // let other broadcasters know that a manager has been terminated
      replicator ! Update(ManagersKey, ORSet.empty[ActorRef], WriteAll(10.seconds))(_.remove(terminatedManager))
      context.become(receiveS(managers - terminatedManager, update(workList, terminatedManager)))
  }

  private def watch(managers: Set[ActorRef]): Unit =
    managers.foreach(context.watch)

  /**
    * Removes the `stopWaitingFor` actor from the `workList` and "forgets" about
    * work elements that, after removal, are not waiting on any manager anymore.
    */
  private def update(workList: Map[UUID, Work], stopWaitingFor: ActorRef): Map[UUID, Work] =
    workList.toList.mapFilter {
      case (id, Work(waitingFor, replyTo, response)) =>
        val waitingFor0 = waitingFor - stopWaitingFor
        if (waitingFor0.isEmpty) None
        else Some((id, Work(waitingFor0, replyTo, response)))
    }.toMap
}

object BroadcastActor {

  /**
    *  Wrapper around [[RequestEvent]]s to signal that the request was sent from a manager's broadcaster.
    * @param request the wrapped request.
    * @param replyTo the actor that originated the request.
    * @param id the unique identifier of the request.
    */
  final case class BroadcastRequest(request: RequestEvent, initResponse: ResponseEvent, replyTo: ActorRef, id: UUID) {

    /**
      * @see [[BroadcastResponse.fromBroadcastedRequest()]]
      */
    def respondWith(response: ResponseEvent): BroadcastResponse =
      BroadcastResponse.fromBroadcastedRequest(this, response)
  }

  object BroadcastRequest {

    /**
      * Returns a `BroadcastRequest` with a random id.
      */
    def create(request: RequestEvent, initResponse: ResponseEvent, replyTo: ActorRef): BroadcastRequest =
      BroadcastRequest(request, initResponse, replyTo, UUID.randomUUID())
  }

  /**
    * Wrapper around [[ResponseEvent]] to signal that the [[response]] is an answer to a [[BroadcastRequest]].
    *
    * @param response the wrapped response.
    * @param id the unique identifier of the response. Must be the same as the request it responds to!
    */
  final case class BroadcastResponse(response: ResponseEvent, id: UUID)

  object BroadcastResponse {

    /**
      * Helper to build a [[BroadcastResponse]] from a [[BroadcastRequest]] that inherits its id.
      */
    def fromBroadcastedRequest(br: BroadcastRequest, response: ResponseEvent): BroadcastResponse =
      BroadcastResponse(response, br.id)
  }

  /**
    * Work element tracking the replies of managers.
    *
    * @param waitingFor the managers whose responses have not been received.
    * @param replyTo the actor that is waiting for the response.
    * @param response the current response being built up.
    */
  final case class Work(waitingFor: Set[ActorRef], replyTo: ActorRef, response: ResponseEvent)

  def props(manager: ActorRef): Props = Props(new BroadcastActor(manager))
}
