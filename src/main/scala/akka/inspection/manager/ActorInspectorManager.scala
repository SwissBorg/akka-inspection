package akka.inspection.manager

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.inspection.ActorInspection.FinalizedFragment
import akka.inspection.{ActorInspection, FragmentId}
import akka.inspection.manager.BroadcastActor._
import akka.inspection.manager.state._
import akka.stream.{ActorMaterializer, Materializer, QueueOfferResult}
import cats.data.OptionT
import cats.implicits._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * An actor that manages all the inspection requests.
  *
  * The manager only handles actors within its own node. There is one instance per node started by
  * the actor inspection extension.
  */
class ActorInspectorManager extends Actor with ActorLogging {
  implicit private val ec: ExecutionContext = context.dispatcher
  implicit private val mat: Materializer    = ActorMaterializer()

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
        case Failure(t)              => log.error(t.toString)
      }
  }

  /**
    * Handles responses received from inspectable actors.
    */
  private def inspectableActorsResponses: Receive = {
    def convertFragmentIdsResponse(state: String, fragmentIds: Set[FragmentId]): FragmentIdsResponse =
      FragmentIdsResponse(Either.right((state, fragmentIds.toList)))

    def convertFragmentsResponse(state: String, fragments: Map[FragmentId, FinalizedFragment]): FragmentsResponse =
      FragmentsResponse(Either.right((state, fragments)))

    // When an `id` is receive with the response this means that it has been originally been broadcast as it added
    // one to be able to track all the different broadcasts it sent out.
    {
      case ActorInspection.FragmentIdsResponse(state, fragmentIds, originalRequester, id) =>
        id match {
          case Some(id) => originalRequester ! BroadcastResponse(convertFragmentIdsResponse(state, fragmentIds), id)
          case None     => originalRequester ! convertFragmentIdsResponse(state, fragmentIds)
        }

      case ActorInspection.FragmentsResponse(state, fragments, originalRequester, id) =>
        id match {
          case Some(id) => originalRequester ! BroadcastResponse(convertFragmentsResponse(state, fragments), id)
          case None     => originalRequester ! convertFragmentsResponse(state, fragments)
        }
    }
  }

  /**
    * Handles the subscription events.
    */
  private def subscriptionEvents(s: State): Receive = {
    case Subscribe(ref, groups0) =>
      context.watchWith(ref.ref, Unsubscribe(ref))
      context.become(receiveS(s.subscribe(ref, groups0)))

    case Unsubscribe(ref) =>
      context.become(receiveS(s.unsubscribe(ref)))
  }

  /**
    * Handles the inspection specific requests.
    */
  private def infoRequests(s: State): Receive = {
    case r: RequestEvent =>
      val replyTo = sender() // bind so we do not capture it in the future's callback

      responseTo(r, s, replyTo).value.onComplete {
        case Success(Some(response)) =>
          response match {
            // Another manager might be able to respond.
            case GroupsResponse(Left(_))      => broadcaster ! BroadcastRequest.create(r, response, replyTo)
            case FragmentsResponse(Left(_))   => broadcaster ! BroadcastRequest.create(r, response, replyTo)
            case FragmentIdsResponse(Left(_)) => broadcaster ! BroadcastRequest.create(r, response, replyTo)

            // Request that have to be broadcast to be fully answered.
            case _: GroupResponse             => broadcaster ! BroadcastRequest.create(r, response, replyTo)
            case _: InspectableActorsResponse => broadcaster ! BroadcastRequest.create(r, response, replyTo)

            // An inspectable actor only exists in a single manager.
            // So successful response to requests related to one actor
            // don't have to be broadcast.
            case GroupsResponse(Right(_))      => replyTo ! response
            case FragmentIdsResponse(Right(_)) => replyTo ! response

            case other => log.warning(s"Did not handle: $other")
          }

        case Success(None) => () // no response to send

        case Failure(t) =>
          log.error(t.toString)
      }
  }

  /**
    * Responds to the request with all the information available. For some types of requests
    * this is sufficient to fully answer the request. However, in some cases the information
    * is not available in a single node and thus will have to be broadcast to managers on the
    * other nodes.
    *
    * @param request           the request to respond to.
    * @param s                 the state of the actor.
    * @param originalRequester who to reply to in case of a `ActorInspection.FragmentRequest`.
    * @param id                the id of the request if available.
    * @return a potential response.
    */
  private def _responseTo(
      request: RequestEvent,
      s: State,
      originalRequester: ActorRef,
      id: Option[UUID]
  ): OptionT[Future, ResponseEvent] =
    request match {
      case InspectableActorsRequest =>
        OptionT.pure(InspectableActorsResponse(s.inspectableActorRefs.toList.map(_.toId)))
      case GroupsRequest(id)   => OptionT.pure(GroupsResponse(s.groupsOf(id).map(_.toList)))
      case GroupRequest(group) => OptionT.pure(GroupResponse(s.inGroup(group).toList.map(_.toId)))

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

          case Left(err) =>
            OptionT.pure(FragmentsResponse(Either.left(err)))
        }

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

  private def responseToBroadcast(
      request: BroadcastRequest,
      s: State,
      originalRequester: ActorRef
  ): OptionT[Future, ResponseEvent] =
    _responseTo(request.request, s, originalRequester, Some(request.id))

  private def responseTo(request: RequestEvent, s: State, originalRequester: ActorRef): OptionT[Future, ResponseEvent] =
    _responseTo(request, s, originalRequester, None)
}

object ActorInspectorManager {

  /**
    * An [[ActorRef]] that can be inspected.
    */
  final case class InspectableActorRef private[inspection] (ref: ActorRef) {
    val toId: String = s"${ref.path}"
  }

  def props(): Props = Props(new ActorInspectorManager)
}
