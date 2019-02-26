package akka.inspection

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.inspection.ActorInspection._
import akka.inspection.ActorInspectorImpl.InspectableActorRef
import akka.inspection.ActorInspectorManager.Groups.Group
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy, QueueOfferResult}
import monocle.{Iso, Prism}

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
    case p@Put(ref, keys0, groups0) =>
      println(s)
      println(p)
      val s0 = s.put(ref, keys0, groups0)
      println(s0)
      context.become(statefulReceive(s0))
    case Release(ref)             => context.become(statefulReceive(s.release(ref)))
  }

  def infoRequests(s: State[ActorInspection.FragmentsRequest]): Receive = {
    case InspectableActorsRequest => sender() ! InspectableActorsResponse(s.inspectableActorIds.toList)
    case GroupsRequest(id)        => sender() ! GroupsResponse(s.groups(id).map(_.toList))
    case FragmentIdsRequest(id)   => sender() ! FragmentIdsResponse(s.stateFragmentIds(id).map(_.toList))
    case GroupRequest(group)      => sender() ! GroupResponse(s.inGroup(group))
  }
}

object ActorInspectorManager {
  case class State[M](
    private val inspectableActors: InspectableActors,
    private val stateFragments: StateFragments,
    private val groups: Groups,
    private val sourceQueues: SourceQueues[M]
  )(implicit context: ExecutionContext, materializer: Materializer) {
    def put(ref: InspectableActorRef, keys: Set[FragmentId], groups: Set[Group]): State[M] =
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

    def stateFragmentIds(id: String): Either[ActorNotInspectable, Set[FragmentId]] =
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
  final case class StateFragments(private val fragmentIds: Map[InspectableActorRef, Set[FragmentId]]) extends AnyVal {
    def addStateFragment(ref: InspectableActorRef, keys: Set[FragmentId]): StateFragments =
      copy(fragmentIds = this.fragmentIds + (ref -> keys))
    def remove(ref: InspectableActorRef): StateFragments = copy(fragmentIds = fragmentIds - ref)
    def stateFragmentsIds(ref: InspectableActorRef): Set[FragmentId] = fragmentIds.getOrElse(ref, Set.empty)
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

  final case class Put(ref: InspectableActorRef, keys: Set[FragmentId], groups: Set[Group]) extends Event
  final case class Release(ref: InspectableActorRef) extends Event

  final case object InspectableActorsRequest extends Event

  final case class InspectableActorsResponse(inspectable: List[String]) extends Event {
    val toGRPC: grpc.InspectableActorsResponse = InspectableActorsResponse.grpcIso(this)
  }
  object InspectableActorsResponse {
    def fromGRPC(r: grpc.InspectableActorsResponse): InspectableActorsResponse = grpcIso.get(r)

    val grpcIso: Iso[grpc.InspectableActorsResponse, InspectableActorsResponse] =
      Iso[grpc.InspectableActorsResponse, InspectableActorsResponse](
        r => InspectableActorsResponse(r.inspectableActors.toList)
      )(r => grpc.InspectableActorsResponse(r.inspectable))
  }

  final case class GroupsRequest(path: String) extends Event {
    val toGRPC: grpc.GroupsRequest = GroupsRequest.grpcIso(this)
  }
  object GroupsRequest {
    def fromGRPC(r: grpc.GroupsRequest): GroupsRequest = grpcIso.get(r)

    val grpcIso: Iso[grpc.GroupsRequest, GroupsRequest] =
      Iso[grpc.GroupsRequest, GroupsRequest](r => GroupsRequest(r.actor))(r => grpc.GroupsRequest(r.path))
  }

  final case class GroupsResponse(group: Either[ActorNotInspectable, List[Group]]) extends Event {
    val toGRPC: grpc.GroupsResponse = GroupsResponse.grpcPrism(this)
  }

  object GroupsResponse {
    def fromGRPC(r: grpc.GroupsResponse): Option[GroupsResponse] = grpcPrism.getOption(r)

    val grpcPrism: Prism[grpc.GroupsResponse, GroupsResponse] =
      Prism.partial[grpc.GroupsResponse, GroupsResponse] {
        case grpc.GroupsResponse(grpc.GroupsResponse.Res.Groups(grpc.GroupsResponse.Groups(groups))) =>
          GroupsResponse(Right(groups.map(Group).toList))
        case grpc.GroupsResponse(grpc.GroupsResponse.Res.Error(grpc.Error.ActorNotInspectable(id))) =>
          GroupsResponse(Left(ActorNotInspectable(id)))
      } {
        case GroupsResponse(Right(groups)) =>
          grpc.GroupsResponse(grpc.GroupsResponse.Res.Groups(grpc.GroupsResponse.Groups(groups.map(_.name))))
        case GroupsResponse(Left(ActorNotInspectable(id))) =>
          grpc.GroupsResponse(grpc.GroupsResponse.Res.Error(grpc.Error.ActorNotInspectable(id)))
      }
  }

  final case class GroupRequest(group: Group) extends Event
  final case class GroupResponse(paths: Set[InspectableActorRef]) extends Event

  final case class FragmentIdsRequest(path: String) extends Event {
    val toGRPC: grpc.FragmentIdsRequest = FragmentIdsRequest.grpcIso(this)
  }
  object FragmentIdsRequest {
    def fromGRPC(r: grpc.FragmentIdsRequest): FragmentIdsRequest = grpcIso.get(r)

    val grpcIso: Iso[grpc.FragmentIdsRequest, FragmentIdsRequest] =
      Iso[grpc.FragmentIdsRequest, FragmentIdsRequest](r => FragmentIdsRequest(r.actor))(
        r => grpc.FragmentIdsRequest(r.path)
      )
  }

  final case class FragmentIdsResponse(keys: Either[ActorNotInspectable, List[FragmentId]]) extends Event {
    val toGRPC: grpc.FragmentIdsResponse = FragmentIdsResponse.grpcPrism(this)
  }
  object FragmentIdsResponse {
    def fromGRPC(r: grpc.FragmentIdsResponse): Option[FragmentIdsResponse] = grpcPrism.getOption(r)

    def grpcPrism: Prism[grpc.FragmentIdsResponse, FragmentIdsResponse] =
      Prism.partial[grpc.FragmentIdsResponse, FragmentIdsResponse] {
        case grpc.FragmentIdsResponse(
            grpc.FragmentIdsResponse.Res.FragmentIds(grpc.FragmentIdsResponse.FragmentIds(ids))
            ) =>
          FragmentIdsResponse(Right(ids.map(FragmentId).toList))

        case grpc.FragmentIdsResponse(
            grpc.FragmentIdsResponse.Res.Error(grpc.Error.ActorNotInspectable(id))
            ) =>
          FragmentIdsResponse(Left(ActorNotInspectable(id)))
      } {
        case FragmentIdsResponse(Right(fragmentIds)) =>
          grpc.FragmentIdsResponse(
            grpc.FragmentIdsResponse.Res.FragmentIds(grpc.FragmentIdsResponse.FragmentIds(fragmentIds.map(_.id)))
          )

        case FragmentIdsResponse(Left(ActorNotInspectable(id))) =>
          grpc.FragmentIdsResponse(grpc.FragmentIdsResponse.Res.Error(grpc.Error.ActorNotInspectable(id)))
      }
  }

  final case class FragmentsRequest(fragmentIds: List[FragmentId], id: String) extends Event {
    def toGRPC: grpc.FragmentsRequest = FragmentsRequest.grpcIso(this)
  }
  object FragmentsRequest {
    def fromGRPC(r: grpc.FragmentsRequest): FragmentsRequest = grpcIso.get(r)

    val grpcIso: Iso[grpc.FragmentsRequest, FragmentsRequest] =
      Iso[grpc.FragmentsRequest, FragmentsRequest](
        r => FragmentsRequest(r.fragmentIds.map(FragmentId).toList, r.actor)
      )(
        r => grpc.FragmentsRequest(r.id, r.fragmentIds.map(_.id))
      )
  }

  final case class FragmentsResponse(fragments: Either[Error, Map[FragmentId, FinalizedFragment]]) extends Event {
    val toGRPC: grpc.FragmentsResponse = FragmentsResponse.grpcPrism(this)
  }
  object FragmentsResponse {
    def fromGRPC(r: grpc.FragmentsResponse): Option[FragmentsResponse] = grpcPrism.getOption(r)

    val grpcPrism: Prism[grpc.FragmentsResponse, FragmentsResponse] =
      Prism.partial[grpc.FragmentsResponse, FragmentsResponse] {
        case grpc.FragmentsResponse(
            grpc.FragmentsResponse.Res.Fragments(grpc.FragmentsResponse.Fragments(fragments))
            ) =>
          FragmentsResponse(Right(fragments.map {
            case (k, grpc.FragmentsResponse.Fragment(grpc.FragmentsResponse.Fragment.Res.Fragment(f))) =>
              (FragmentId(k), RenderedFragment(f))
            case (k, grpc.FragmentsResponse.Fragment(grpc.FragmentsResponse.Fragment.Res.Empty)) =>
              (FragmentId(k), UndefinedFragment)
          }))
        case grpc.FragmentsResponse(
            grpc.FragmentsResponse.Res.Error(
              grpc
                .Error(grpc.Error.Error.ActorNotInspectable(grpc.Error.ActorNotInspectable(id)))
            )
            ) =>
          FragmentsResponse(Left(ActorNotInspectable(id)))

        case grpc.FragmentsResponse(
            grpc.FragmentsResponse.Res.Error(
              grpc
                .Error(grpc.Error.Error.UnreachableInspectableActor(grpc.Error.UnreachableInspectableActor(id)))
            )
            ) =>
          FragmentsResponse(Left(UnreachableInspectableActor(id)))
      } {
        case FragmentsResponse(Right(fragments)) =>
          grpc.FragmentsResponse(grpc.FragmentsResponse.Res.Fragments(grpc.FragmentsResponse.Fragments(fragments.map {
            case (k, UndefinedFragment) =>
              (k.id, grpc.FragmentsResponse.Fragment(grpc.FragmentsResponse.Fragment.Res.Empty))
            case (k, RenderedFragment(fragment)) =>
              (k.id, grpc.FragmentsResponse.Fragment(grpc.FragmentsResponse.Fragment.Res.Fragment(fragment)))
          })))
        case FragmentsResponse(Left(err)) =>
          err match {
            case ActorNotInspectable(id) =>
              grpc.FragmentsResponse(
                grpc.FragmentsResponse.Res
                  .Error(grpc.Error(grpc.Error.Error.ActorNotInspectable(grpc.Error.ActorNotInspectable(id))))
              )
            case UnreachableInspectableActor(id) =>
              grpc.FragmentsResponse(
                grpc.FragmentsResponse.Res.Error(
                  grpc.Error(grpc.Error.Error.UnreachableInspectableActor(grpc.Error.UnreachableInspectableActor(id)))
                )
              )
          }
      }
  }

  final case class StateRequest(ref: ActorRef) extends Event

  sealed abstract class Error extends Product with Serializable
  final case class ActorNotInspectable(id: String) extends Error
  final case class UnreachableInspectableActor(id: String) extends Error
}
