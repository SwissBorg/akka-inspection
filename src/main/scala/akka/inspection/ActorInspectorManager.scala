package akka.inspection

import akka.actor.{Actor, ActorContext, ActorRef}
import akka.inspection.ActorInspectorImpl.InspectableActorRef
import akka.inspection.ActorInspectorManager.Groups.Group
import akka.inspection.ActorInspectorManager.StateFragments.StateFragmentId
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueue}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy, QueueOfferResult}

import scala.concurrent.{ExecutionContext, Future}

class ActorInspectorManager extends Actor {
  import ActorInspectorManager._

  override def receive: Receive = mainReceive(State.empty)

  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

  implicit val ec: ExecutionContext = context.system.getDispatcher

  def mainReceive(s: State[ActorInspection.StateFragmentRequest]): Receive = {
    case Put(ref, keys0, groups0) => context.become(mainReceive(s.put(ref, keys0, groups0)))
    case Release(ref)             => context.become(mainReceive(s.release(ref)))

    case _: QueryableActorsRequest.type =>
      sender() ! InspectableActorsResponse(s.inspectableActorIds)

    case ActorGroupsRequest(id) =>
      sender() ! ActorGroupsResponse(s.groups(id))

    case StateFragmentIdsRequest(id) =>
      sender() ! StateFragmentIdsResponse(s.stateFragmentIds(id))

    case GroupRequest(group) =>
      sender() ! GroupResponse(s.inGroup(group))

    case StateFragmentsRequest(fragments, id) =>
      val replyTo = sender()
      s.offer(ActorInspection.StateFragmentRequest(fragments, replyTo), id) match {
        case Right(m) =>
          m.foreach {
            case _: QueueOfferResult.Enqueued.type    => () // inspectable actor will receive the request
            case _: QueueOfferResult.Dropped.type     => replyTo ! Left(UnreachableInspectableActor(id))
            case _: QueueOfferResult.Failure          => replyTo ! Left(UnreachableInspectableActor(id))
            case _: QueueOfferResult.QueueClosed.type => replyTo ! Left(UnreachableInspectableActor(id))
          }
        case Left(err) => replyTo ! Left(err)
      }

//      case StateFragmentResponse
  }

  def polling(s: State[StateFragmentsRequest]): Receive = ???
}

object ActorInspectorManager {
  case class State[M](private val inspectableActors: InspectableActors,
                      private val stateFragments: StateFragments,
                      private val groups: Groups,
                      private val sourceQueues: SourceQueues[M]) {
    def put(ref: InspectableActorRef, keys: Set[StateFragmentId], groups: Set[Group])(implicit context: ActorContext,
                                                                                      mat: Materializer): State[M] =
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
    ): Either[ActorNotInspectable.type, Future[QueueOfferResult]] =
      inspectableActors.fromId(id).map(sourceQueues.offer(m, _))

    def inspectableActorIds: Set[String] = inspectableActors.actorIds

    def groups(id: String): Either[ActorNotInspectable.type, Set[Group]] =
      inspectableActors.fromId(id).map(groups.groups)

    def inGroup(g: Group): Set[InspectableActorRef] = groups.inGroup(g)

    def stateFragmentIds(id: String): Either[ActorNotInspectable.type, Set[StateFragmentId]] =
      inspectableActors.fromId(id).map(stateFragments.stateFragmentsIds)
  }

  object State {
    def empty[M]: State[M] =
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

    def actorIds: Set[String] = actors.map(toId)

    def toId(ref: InspectableActorRef): String = ref.ref.path.address.toString

    def fromId(s: String): Either[ActorNotInspectable.type, InspectableActorRef] =
      actors.find(toId(_) == s).toRight(ActorNotInspectable)
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

    /**
     * Represents the identifier of a subset of an actor's state.
     */
    final case class StateFragmentId(id: String) extends AnyVal

    val empty: StateFragments = StateFragments(Map.empty)
  }

  /**
   * Manages the
   */
  final case class SourceQueues[M](private val sourceQueues: Map[InspectableActorRef, SourceQueue[M]]) {
    def add(ref: InspectableActorRef)(implicit mat: Materializer): SourceQueues[M] =
      copy(
        sourceQueues = sourceQueues + (ref -> Source
          .queue[M](5, OverflowStrategy.backpressure)
          .toMat(Sink.actorRefWithAck(ref.ref, ???, ???, ???))(Keep.left)
          .run())
      )

    def remove(ref: InspectableActorRef): SourceQueues[M] = copy(sourceQueues = sourceQueues - ref)

    def offer(m: M, ref: InspectableActorRef)(implicit ec: ExecutionContext): Future[QueueOfferResult] =
      sourceQueues(ref).offer(m)
  }

  object SourceQueues {
    def empty[M]: SourceQueues[M] = SourceQueues(Map.empty)
  }

  sealed abstract class Event extends Product with Serializable

  final case class Put(ref: InspectableActorRef, keys: Set[StateFragmentId], groups: Set[Group]) extends Event
  final case class Release(ref: InspectableActorRef) extends Event

  final case object QueryableActorsRequest extends Event
  final case class InspectableActorsResponse(queryable: Set[String]) extends Event

  final case class ActorGroupsRequest(path: String) extends Event
  final case class ActorGroupsResponse(group: Either[ActorNotInspectable.type, Set[Group]]) extends Event

  final case class GroupRequest(group: Group) extends Event
  final case class GroupResponse(paths: Set[InspectableActorRef]) extends Event

  final case class StateFragmentIdsRequest(path: String) extends Event
  final case class StateFragmentIdsResponse(keys: Either[ActorNotInspectable.type, Set[StateFragmentId]]) extends Event

  final case class StateFragmentsRequest(ids: List[StateFragmentId], id: String) extends Event

  sealed abstract class StateFragmentResponse extends Event
  final case class StateFragmentResponseError(err: Error) extends StateFragmentResponse
  final case class StateFragmentResponseSuccess(s: String) extends StateFragmentResponse

  final case class StateRequest(ref: ActorRef) extends Event

  // TODO move
  sealed abstract class Error extends Product with Serializable
  final case class ActorNotInspectable(id: String) extends Error
  final case class UnreachableInspectableActor(id: String) extends Error
}
