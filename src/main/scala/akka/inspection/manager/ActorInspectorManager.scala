package akka.inspection.manager

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import akka.inspection.ActorInspection
import akka.inspection.ActorInspection.{FinalizedFragment, FragmentId}
import akka.inspection.manager.BroadcastActor._
import akka.inspection.manager.state._
import akka.stream.{ActorMaterializer, Materializer, QueueOfferResult}
import cats.data.OptionT
import cats.implicits._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Manages all the requests to inspect actors.
 *
 */
class ActorInspectorManager extends Actor {
  implicit private val ec: ExecutionContext = context.dispatcher
  implicit private val mat: Materializer = ActorMaterializer()

  /**
   * Broadcaster handling requests that cannot be fully answered by the manager.
   */
  private val broadcaster = context.actorOf(BroadcastActor.props(self))

  override def receive: Receive = receiveS(State.empty)

  private def receiveS(s: State): Receive =
    broadcastRequests(s)
      .orElse(subscriptionEvents(s))
      .orElse(infoRequests(s))
      .orElse(inspectableActorsResponses)

  /**
   * Handles broadcast requests sent by other manager's broadcaster.
   */
  private def broadcastRequests(s: State): Receive = {
    case request: BroadcastRequest =>
      val replyTo = sender()

      responseToBroadcast(request, s, replyTo).value.onComplete {
        case Success(Some(response)) => replyTo ! request.respondWith(response)
        case Success(None)           => () // no need to respond
        case Failure(t)              => throw new IllegalStateException(t)
      }
  }

  /**
   * Handles responses received from inspectable actors.
   */
  private def inspectableActorsResponses: Receive = {
    def convertFragmentIdsResponse(state: String, fragmentIds: Set[FragmentId]): FragmentIdsResponse =
      FragmentIdsResponse(Either.right((state, fragmentIds.toList)))

    // TODO some lense?
    def convertFragmentsResponse(fragments: Map[FragmentId, FinalizedFragment]): FragmentsResponse =
      FragmentsResponse(Either.right(fragments))

    {
      case ActorInspection.FragmentIdsResponse(state, fragmentIds, originalRequester, id) =>
        id match {
          case Some(id) => originalRequester ! BroadcastResponse(convertFragmentIdsResponse(state, fragmentIds), id)
          case None     => originalRequester ! convertFragmentIdsResponse(state, fragmentIds)
        }

      case ActorInspection.FragmentsResponse(_, fragments, initiator, id) =>
        id match {
          case Some(id) => initiator ! BroadcastResponse(convertFragmentsResponse(fragments), id)
          case None     => initiator ! convertFragmentsResponse(fragments)
        }
    }
  }

  /**
   * Handles the subscription events.
   */
  private def subscriptionEvents(s: State): Receive = {
    case Put(ref, keys0, groups0) => context.become(receiveS(s.put(ref, keys0, groups0)))
    case Release(ref)             => context.become(receiveS(s.release(ref)))
  }

  /**
   * Handles the inspection specific requests.
   */
  private def infoRequests(s: State): Receive = {
    case r: RequestEvent =>
      val replyTo = sender()
      responseTo(r, s, replyTo).value.onComplete {
        case Success(Some(response)) =>
          response match {
            /* Another manager might be able to respond. */
            case GroupsResponse(Left(_))      => broadcaster ! BroadcastRequest(r, response, replyTo)
            case FragmentsResponse(Left(_))   => broadcaster ! BroadcastRequest(r, response, replyTo)
            case FragmentIdsResponse(Left(_)) => broadcaster ! BroadcastRequest(r, response, replyTo)

            /* Request that have to be broadcast to be fully answered. */
            case _: GroupResponse             => broadcaster ! BroadcastRequest(r, response, replyTo)
            case _: InspectableActorsResponse => broadcaster ! BroadcastRequest(r, response, replyTo)

            /*
             An inspectable actor only exists in a single manager.
             So successful response to requests related to one actor
             don't have to be broadcast.
             */
            case GroupsResponse(Right(_))      => replyTo ! response
            case FragmentIdsResponse(Right(_)) => replyTo ! response

            case _ => throw new IllegalStateException(s"$response")
          }
        case Success(None) => () // no response to send or the future failed
        case Failure(t)    => throw new IllegalStateException(t)
      }
  }

  private def responseToBroadcast(request: BroadcastRequest,
                                  s: State,
                                  originalRequester: ActorRef): OptionT[Future, ResponseEvent] =
    _responseTo(request.request, s, originalRequester, Some(request.id))

  private def responseTo(request: RequestEvent, s: State, originalRequester: ActorRef): OptionT[Future, ResponseEvent] =
    _responseTo(request, s, originalRequester, None)

  /**
   * Create a response to the `request`.
   * @param request the request to respond to.
   * @param s the state of the actor.
   * @param originalRequester who to reply to in case of a `ActorInspection.FragmentRequest`.
   * @param id the id of the request if available.
   * @return a potential response.
   */
  private def _responseTo(request: RequestEvent,
                          s: State,
                          originalRequester: ActorRef,
                          id: Option[UUID]): OptionT[Future, ResponseEvent] =
    request match {
      case InspectableActorsRequest => OptionT.pure(InspectableActorsResponse(s.inspectableActorIds.toList.map(_.toId)))
      case GroupsRequest(id)        => OptionT.pure(GroupsResponse(s.groups(id).map(_.toList)))
      case GroupRequest(group)      => OptionT.pure(GroupResponse(s.inGroup(group).toList.map(_.toId)))

      case AllFragmentsRequest(actor) => _responseTo(FragmentsRequest(List.empty, actor), s, originalRequester, id)

      case FragmentsRequest(fragments, actor) =>
        s.offer(ActorInspection.FragmentsRequest(fragments, self, originalRequester, id), actor) match {
          case Right(m) =>
            OptionT[Future, ResponseEvent](m.map {
              case QueueOfferResult.Enqueued =>
                None // the inspectable actor will receive the request and should respond back
              case QueueOfferResult.Dropped =>
                Some(FragmentsResponse(Either.left(UnreachableInspectableActor(actor))))
              case _: QueueOfferResult.Failure =>
                Some(FragmentsResponse(Either.left(UnreachableInspectableActor(actor))))
              case QueueOfferResult.QueueClosed =>
                Some(FragmentsResponse(Either.left(UnreachableInspectableActor(actor))))
            })

          case Left(err) => OptionT.pure(FragmentsResponse(Either.left(err)))
        }

      // TODO duplication with above
      // TODO not always go to actor?
      case FragmentIdsRequest(actor) =>
        s.offer(ActorInspection.FragmentIdsRequest(self, originalRequester, id), actor) match {
          case Right(m) =>
            OptionT[Future, ResponseEvent](m.map {
              case QueueOfferResult.Enqueued =>
                None // the inspectable actor will receive the request and should respond back
              case QueueOfferResult.Dropped =>
                Some(FragmentIdsResponse(Either.left(UnreachableInspectableActor(actor))))
              case _: QueueOfferResult.Failure =>
                Some(FragmentIdsResponse(Either.left(UnreachableInspectableActor(actor))))
              case QueueOfferResult.QueueClosed =>
                Some(FragmentIdsResponse(Either.left(UnreachableInspectableActor(actor))))
            })

          case Left(err) => OptionT.pure(FragmentIdsResponse(Either.left(err)))
        }

    }
}

object ActorInspectorManager {

  /**
   * An [[ActorRef]] that can be inspected.
   */
  sealed abstract case class InspectableActorRef(ref: ActorRef) {
    val toId: String = ref.path.toString // TODO render?
  }

  object InspectableActorRef {
    private[inspection] def apply(ref: ActorRef): InspectableActorRef = new InspectableActorRef(ref) {}
  }

  def props(): Props = Props(new ActorInspectorManager)
}
