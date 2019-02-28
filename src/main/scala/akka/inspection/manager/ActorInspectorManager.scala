package akka.inspection.manager

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata._
import akka.inspection.ActorInspection
import akka.inspection.manager.BroadcastActor.{BroadcastResponse, BroadcastedRequest}
import akka.inspection.manager.state._
import akka.stream.{ActorMaterializer, Materializer, QueueOfferResult}
import cats.data.OptionT
import cats.implicits._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/**
 * Manages all the requests to inspect actors.
 *
 * WARNING: needs to be singleton!
 */
class ActorInspectorManager extends Actor with ActorLogging {

  private val router = context.system.actorOf(BroadcastActor.props(self, "actor-inspector-managers"))

  implicit private val ec: ExecutionContext = context.dispatcher
  implicit private val mat: Materializer = ActorMaterializer()

  private val replicator = DistributedData(context.system).replicator
  implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

  // Setup
  val DataKey: ORSetKey[ActorRef] = ORSetKey[ActorRef]("actor-inspector-managers")
  replicator ! Update(DataKey, ORSet.empty[ActorRef], WriteLocal)(_ :+ self)

  override def receive: Receive = statefulReceive(State.empty)

  private def statefulReceive(s: State): Receive =
    distributedRequests.orElse(broadcastedRequests(s)).orElse(subscriptionRequests(s)).orElse(infoRequests(s)).orElse(bla)

  private def broadcastedRequests(s: State): Receive = {
    case br @ BroadcastedRequest(request, id) =>
      log.debug(br.toString)
      val replyTo = sender()

      responseTo(request, s, Some(id)).map(br.response).value.onComplete {
        case Success(Some(response)) => replyTo ! response
        case _                       => ()
      }
  }

  private def bla: Receive = {
    case ActorInspection.FragmentsResponse(fragments, initiator, id) =>
      val response = id match {
        case Some(id) => BroadcastResponse(FragmentsResponse(Either.right(fragments)), id)
        case None     => FragmentsResponse(Either.right(fragments))
      }

      initiator ! response
  }

  private def subscriptionRequests(s: State): Receive = {
    case r @ Put(ref, keys0, groups0) =>
      log.debug(r.toString)
      context.become(statefulReceive(s.put(ref, keys0, groups0)))
    case r @ Release(ref) =>
      log.debug(r.toString)
      context.become(statefulReceive(s.release(ref)))
  }

  private def distributedRequests: Receive = {
    case r: GroupRequest =>
      log.debug(r.toString)
      router ! r
    case r: InspectableActorsRequest.type =>
      log.debug(r.toString)
      router ! r
  }

  private def infoRequests(s: State): Receive = {
    case r: RequestEvent =>
      log.debug(r.toString)
      val replyTo = sender()
      responseTo(r, s).value.onComplete {
        case Success(Some(response)) =>
          response match {
            case GroupsResponse(Left(ActorNotInspectable(_))) =>
              // the actor might exist in another manager
              router ! r
            case _: InspectableActorsResponse =>
              throw new IllegalStateException("'InspectableActorsResponse' messages should not be handled here.")
            case _: GroupResponse =>
              throw new IllegalStateException("'GroupRequest' messages should not be handled here.")
            case _ => replyTo ! response
          }
        case r =>
          log.debug(s"HERE! $r")
          () // no response to send or the future failed
      }
  }

  def responseTo(request: RequestEvent, s: State, id: Option[UUID] = None): OptionT[Future, ResponseEvent] =
    request match {
      case InspectableActorsRequest => OptionT.pure(InspectableActorsResponse(s.inspectableActorIds.toList))
      case GroupsRequest(id)        => OptionT.pure(GroupsResponse(s.groups(id).map(_.toList)))
      case GroupRequest(group)      => OptionT.pure(GroupResponse(s.inGroup(group)))
      case FragmentIdsRequest(id)   => OptionT.pure(FragmentIdsResponse(s.stateFragmentIds(id).map(_.toList)))

      case FragmentsRequest(fragments, actor) =>
        val initiator = sender()
        s.offer(ActorInspection.FragmentsRequest(fragments, self, initiator, id), actor) match {
          case Right(m) =>
            OptionT[Future, ResponseEvent](m.map {
              case QueueOfferResult.Enqueued =>
                log.debug("enqueue")
                None // inspectable actor will receive the request
              case QueueOfferResult.Dropped =>
                Some(FragmentsResponse(Either.left(UnreachableInspectableActor(actor))))
              case _: QueueOfferResult.Failure =>
                Some(FragmentsResponse(Either.left(UnreachableInspectableActor(actor))))
              case QueueOfferResult.QueueClosed =>
                Some(FragmentsResponse(Either.left(UnreachableInspectableActor(actor))))
            })

          case Left(err) => OptionT.pure(FragmentsResponse(Either.left(err)))
        }
    }

  override def postStop(): Unit = {
    replicator ! Update(DataKey, ORSet.empty[ActorRef], WriteLocal)(_.remove(self))
    super.postStop()
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    replicator ! Update(DataKey, ORSet.empty[ActorRef], WriteLocal)(_.remove(self))
    super.preRestart(reason, message)
  }

  override def postRestart(reason: Throwable): Unit = {
    replicator ! Update(DataKey, ORSet.empty[ActorRef], WriteLocal)(_ :+ self)
    super.postRestart(reason)
  }
}

object ActorInspectorManager {
  def props(): Props = Props(new ActorInspectorManager)
}
