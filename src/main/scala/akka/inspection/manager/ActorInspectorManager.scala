package akka.inspection.manager

import akka.actor.{Actor, ActorLogging}
import akka.inspection.ActorInspection
import akka.inspection.manager.state.{Errors, Events, State}
import akka.stream.{ActorMaterializer, Materializer, QueueOfferResult}

import scala.concurrent.ExecutionContext

/**
 * Manages all the requests to inspect actors.
 *
 * WARNING: needs to be singleton!
 */
class ActorInspectorManager extends Actor with ActorLogging {
  import ActorInspectorManager._

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val mat: Materializer = ActorMaterializer()

  override def receive: Receive = statefulReceive(State.empty)

  private def statefulReceive(s: State[ActorInspection.FragmentsRequest]): Receive =
    fragmentRequests(s).orElse(subscriptionRequests(s)).orElse(infoRequests(s))

  /**
   * Handles the requests for state-fragments.
   *
   * Note: the caller expects a reply of type
   * `Either[ActorInspectorManager.Error, Map[StateFragmentId, FinalizedStateFragment0]`.
   */
  private def fragmentRequests(s: State[ActorInspection.FragmentsRequest]): Receive = {
    case FragmentsRequest(fragments, id) =>
      val initiator = sender()
      println(s"Request: $id")
      println(s)
      val m = s.offer(ActorInspection.FragmentsRequest(fragments, self, initiator), id)
//      println(m)
      m match {
        case Right(m) =>
          m.foreach {
            case QueueOfferResult.Enqueued =>
              println("1")
              () // inspectable actor will receive the request

            case QueueOfferResult.Dropped =>
              println("2")

              initiator ! FragmentsResponse(Left(UnreachableInspectableActor(id)))

            case _: QueueOfferResult.Failure =>
              println("3")

              initiator ! FragmentsResponse(Left(UnreachableInspectableActor(id)))

            case QueueOfferResult.QueueClosed =>
              println("4")

              initiator ! FragmentsResponse(Left(UnreachableInspectableActor(id)))
          }

        case Left(err) =>
          println("5")
          initiator ! FragmentsResponse(Left(err))
      }

    case ActorInspection.FragmentsResponse(fragments, initiator) =>
      initiator ! FragmentsResponse(Right(fragments))
  }

  def subscriptionRequests(s: State[ActorInspection.FragmentsRequest]): Receive = {
    case p @ Put(ref, keys0, groups0) =>
      println(s)
      println(p)
      val s0 = s.put(ref, keys0, groups0)
      println(s0)
      context.become(statefulReceive(s0))
    case Release(ref) => context.become(statefulReceive(s.release(ref)))
  }

  def infoRequests(s: State[ActorInspection.FragmentsRequest]): Receive = {
    case InspectableActorsRequest => sender() ! InspectableActorsResponse(s.inspectableActorIds.toList)
    case GroupsRequest(id)        => sender() ! GroupsResponse(s.groups(id).map(_.toList))
    case FragmentIdsRequest(id)   => sender() ! FragmentIdsResponse(s.stateFragmentIds(id).map(_.toList))
    case GroupRequest(group)      => sender() ! GroupResponse(s.inGroup(group))
  }
}

object ActorInspectorManager extends Events with Errors
