package akka.inspection

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.inspection.ActorInspection.{FinalizedStateFragment0, StateFragment, StateFragmentId, StateFragmentResponse}
import akka.inspection.ActorInspectorImpl.InspectableActorRef
import akka.inspection.ActorInspectorManager.Groups.Group
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy, QueueOfferResult}

import scala.concurrent.{ExecutionContext, Future}

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

  private def statefulReceive(s: State[ActorInspection.StateFragmentRequest]): Receive =
    fragmentRequests(s).orElse(subscriptionRequests(s)).orElse(infoRequests(s))

  /**
   * Handles the requests for state-fragments.
   *
   * Note: the caller expects a reply of type
   * `Either[ActorInspectorManager.Error, Map[StateFragmentId, FinalizedStateFragment0]`.
   */
  private def fragmentRequests(s: State[ActorInspection.StateFragmentRequest]): Receive = {
    case s0 @ StateFragmentsRequest(fragments, id) =>
      log.debug(s0.toString)

      val initiator = sender()
      s.offer(ActorInspection.StateFragmentRequest(fragments, self, initiator), id) match {
        case Right(m) =>
          m.foreach {
            case QueueOfferResult.Enqueued => () // inspectable actor will receive the request

            case QueueOfferResult.Dropped =>
              initiator ! StateFragmentsResponse.Failure(UnreachableInspectableActor(id))

            case _: QueueOfferResult.Failure =>
              initiator ! StateFragmentsResponse.Failure(UnreachableInspectableActor(id))

            case QueueOfferResult.QueueClosed =>
              initiator ! StateFragmentsResponse.Failure(UnreachableInspectableActor(id))
          }

        case Left(err) => initiator ! StateFragmentsResponse.Failure(err)
      }

    case s @ StateFragmentResponse(fragments, initiator) =>
      log.debug(s.toString)
      initiator ! StateFragmentsResponse.Success(fragments)
  }

  def subscriptionRequests(s: State[ActorInspection.StateFragmentRequest]): Receive = {
    case Put(ref, keys0, groups0) => context.become(statefulReceive(s.put(ref, keys0, groups0)))
    case Release(ref)             => context.become(statefulReceive(s.release(ref)))
  }

  def infoRequests(s: State[ActorInspection.StateFragmentRequest]): Receive = {
    case InspectableActorsRequest    => sender() ! InspectableActorsResponse(s.inspectableActorIds)
    case ActorGroupsRequest(id)      => sender() ! ActorGroupsResponse(s.groups(id))
    case StateFragmentIdsRequest(id) => sender() ! StateFragmentIdsResponse(s.stateFragmentIds(id))
    case GroupRequest(group)         => sender() ! GroupResponse(s.inGroup(group))
  }
}

object ActorInspectorManager {
  case class State[M](
    private val inspectableActors: InspectableActors,
    private val stateFragments: StateFragments,
    private val groups: Groups,
    private val sourceQueues: SourceQueues[M]
  )(implicit context: ExecutionContext, materializer: Materializer) {
    def put(ref: InspectableActorRef, keys: Set[StateFragmentId], groups: Set[Group]): State[M] =
      copy(
        inspectableActors = inspectableActors.add(ref),
        stateFragments = this.stateFragments.addStateFragment(ref, keys),
        groups = this.groups.addGroups(ref, groups),
        sourceQueues = sourceQueues.add(ref)
      )

    def release(ref: InspectableActorRef): State[M] =
      copy(
        inspectableActors = inspectableActors.remove(ref),
        stateFragments = stateFragments.remove(ref),
        groups = groups.remove(ref),
        sourceQueues = sourceQueues.remove(ref)
      )

    def offer(m: M, id: String)(
      implicit ec: ExecutionContext
    ): Either[ActorNotInspectable, Future[QueueOfferResult]] =
      inspectableActors.fromId(id).map(sourceQueues.offer(m, _))

    def inspectableActorIds: Set[String] = inspectableActors.actorIds

    def groups(id: String): Either[ActorNotInspectable, Set[Group]] =
      inspectableActors.fromId(id).map(groups.groups)

    def inGroup(g: Group): Set[InspectableActorRef] = groups.inGroup(g)

    def stateFragmentIds(id: String): Either[ActorNotInspectable, Set[StateFragmentId]] =
      inspectableActors.fromId(id).map(stateFragments.stateFragmentsIds)
  }

  object State {
    def empty[M](implicit context: ExecutionContext, materializer: Materializer): State[M] =
      State(inspectableActors = InspectableActors.empty,
            stateFragments = StateFragments.empty,
            groups = Groups.empty,
            sourceQueues = SourceQueues.empty)
  }

  /**
   * Manages the inspectable actors.
   */
  final case class InspectableActors(private val actors: Set[InspectableActorRef]) extends AnyVal {
    def add(ref: InspectableActorRef): InspectableActors =
      copy(actors = actors + ref)

    def remove(ref: InspectableActorRef): InspectableActors = copy(actors = actors - ref)

    def actorRefs: Set[InspectableActorRef] = actors

    def actorIds: Set[String] = actors.map(_.toId)

    def fromId(s: String): Either[ActorNotInspectable, InspectableActorRef] =
      actors.find(_.toId == s).toRight(ActorNotInspectable(s))
  }

  object InspectableActors {
    val empty: InspectableActors = InspectableActors(Set.empty)
  }

  /**
   * Manages the groups in which the inspectable actors belong.
   */
  final case class Groups(private val groups: Map[Group, Set[InspectableActorRef]]) extends AnyVal {
    def addGroups(ref: InspectableActorRef, groups: Set[Group]): Groups =
      copy(groups = groups.foldLeft(this.groups) {
        case (groups, group) => groups + (group -> (groups.getOrElse(group, Set.empty) + ref))
      })

    def remove(ref: InspectableActorRef): Groups =
      copy(groups = groups.map { case (group, refs) => (group, refs - ref) })

    def inGroup(group: Group): Set[InspectableActorRef] = groups.getOrElse(group, Set.empty)

    def groups(ref: InspectableActorRef): Set[Group] = groups.foldLeft(Set.empty[Group]) {
      case (groups, (group, refs)) => if (refs.contains(ref)) groups + group else groups
    }
  }

  object Groups {
    final case class Group(name: String) extends AnyVal

    val empty: Groups = Groups(Map.empty)
  }

  /**
   * Manages the state-fragments of each inspectable actor.
   */
  final case class StateFragments(private val fragmentIds: Map[InspectableActorRef, Set[StateFragmentId]])
      extends AnyVal {
    def addStateFragment(ref: InspectableActorRef, keys: Set[StateFragmentId]): StateFragments =
      copy(fragmentIds = this.fragmentIds + (ref -> keys))
    def remove(ref: InspectableActorRef): StateFragments = copy(fragmentIds = fragmentIds - ref)
    def stateFragmentsIds(ref: InspectableActorRef): Set[StateFragmentId] = fragmentIds.getOrElse(ref, Set.empty)
  }

  object StateFragments {
    val empty: StateFragments = StateFragments(Map.empty)
  }

  /**
   * Manages the
   */
  final case class SourceQueues[M](private val sourceQueues: Map[InspectableActorRef, SourceQueueWithComplete[M]]) {
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

  object SourceQueues {
    def empty[M]: SourceQueues[M] = SourceQueues(Map.empty)
  }

  sealed abstract class Event extends Product with Serializable

  final case class Put(ref: InspectableActorRef, keys: Set[StateFragmentId], groups: Set[Group]) extends Event
  final case class Release(ref: InspectableActorRef) extends Event

  final case object InspectableActorsRequest extends Event
  final case class InspectableActorsResponse(queryable: Set[String]) extends Event

  final case class ActorGroupsRequest(path: String) extends Event
  final case class ActorGroupsResponse(group: Either[ActorNotInspectable, Set[Group]]) extends Event

  final case class GroupRequest(group: Group) extends Event
  final case class GroupResponse(paths: Set[InspectableActorRef]) extends Event

  final case class StateFragmentIdsRequest(path: String) extends Event
  final case class StateFragmentIdsResponse(keys: Either[ActorNotInspectable, Set[StateFragmentId]]) extends Event

  final case class StateFragmentsRequest(fragmentIds: Set[StateFragmentId], id: String) extends Event
  sealed abstract class StateFragmentsResponse extends Event
  object StateFragmentsResponse {
    final case class Success(fragments: Map[StateFragmentId, FinalizedStateFragment0]) extends StateFragmentsResponse
    final case class Failure(error: Error) extends StateFragmentsResponse
  }

  final case class StateRequest(ref: ActorRef) extends Event

  sealed abstract class Error extends Product with Serializable
  final case class ActorNotInspectable(id: String) extends Error
  final case class UnreachableInspectableActor(id: String) extends Error
}
