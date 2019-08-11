package akka.inspection.manager

import akka.inspection.ActorInspection.{FinalizedFragment, RenderedFragment, UndefinedFragment}
import akka.inspection.FragmentId
import akka.inspection.grpc
import akka.inspection.manager.ActorInspectorManager.InspectableActorRef
import akka.inspection.manager.state.Group
import cats.implicits._
import cats.kernel.Semigroup
import monocle.{Iso, Prism}

/**
 * Events handle by the `ActorInspectorManager`
 */
sealed abstract class Event extends RequestEvent with ResponseEvent with SubscriptionEvent

/* --- Subscribtion events --- */
sealed trait SubscriptionEvent extends Product with Serializable

final case class Subscribe(ref: InspectableActorRef, groups: Set[Group]) extends SubscriptionEvent

final case class Unsubscribe(ref: InspectableActorRef) extends SubscriptionEvent

/* --- Request events --- */
sealed trait RequestEvent extends Product with Serializable

final case object InspectableActorsRequest extends RequestEvent {
  def toGRPC: grpc.InspectableActorsRequest = grpcIso(this)

  def fromGRPC(r: grpc.InspectableActorsRequest): InspectableActorsRequest.type = grpcIso.get(r)

  val grpcIso: Iso[grpc.InspectableActorsRequest, InspectableActorsRequest.type] =
    Iso[grpc.InspectableActorsRequest, InspectableActorsRequest.type](_ => InspectableActorsRequest)(
      _ => grpc.InspectableActorsRequest()
    )
}

final case class GroupsRequest(actor: String) extends RequestEvent {
  val toGRPC: grpc.GroupsRequest = GroupsRequest.grpcIso(this)
}

object GroupsRequest {
  def fromGRPC(r: grpc.GroupsRequest): GroupsRequest = grpcIso.get(r)

  val grpcIso: Iso[grpc.GroupsRequest, GroupsRequest] =
    Iso[grpc.GroupsRequest, GroupsRequest](r => GroupsRequest(r.actor))(r => grpc.GroupsRequest(r.actor))
}

final case class GroupRequest(group: Group) extends RequestEvent {
  val toGRPC: grpc.GroupRequest = GroupRequest.grpcIso(this)
}

object GroupRequest {
  def fromGRPC(r: grpc.GroupRequest): GroupRequest = grpcIso.get(r)

  val grpcIso: Iso[grpc.GroupRequest, GroupRequest] =
    Iso[grpc.GroupRequest, GroupRequest](r => GroupRequest(Group(r.group)))(r => grpc.GroupRequest(r.group.name))
}

final case class FragmentIdsRequest(actor: String) extends RequestEvent {
  val toGRPC: grpc.FragmentIdsRequest = FragmentIdsRequest.grpcIso(this)
}
object FragmentIdsRequest {
  def fromGRPC(r: grpc.FragmentIdsRequest): FragmentIdsRequest = grpcIso.get(r)

  val grpcIso: Iso[grpc.FragmentIdsRequest, FragmentIdsRequest] =
    Iso[grpc.FragmentIdsRequest, FragmentIdsRequest](r => FragmentIdsRequest(r.actor))(
      r => grpc.FragmentIdsRequest(r.actor)
    )
}

final case class FragmentsRequest(fragmentIds: List[FragmentId], actor: String) extends RequestEvent {
  def toGRPC: grpc.FragmentsRequest = FragmentsRequest.grpcIso(this)
}

object FragmentsRequest {
  def fromGRPC(r: grpc.FragmentsRequest): FragmentsRequest = grpcIso.get(r)

  val grpcIso: Iso[grpc.FragmentsRequest, FragmentsRequest] =
    Iso[grpc.FragmentsRequest, FragmentsRequest](
      r => FragmentsRequest(r.fragmentIds.map(FragmentId).toList, r.actor)
    )(
      r => grpc.FragmentsRequest(r.actor, r.fragmentIds.map(_.id))
    )
}

/* --- Response events ---- */
sealed trait ResponseEvent extends Product with Serializable

object ResponseEvent {

  /**
   * Merges the [[ResponseEvent]]s together if they match else picks the one on the right.
   */
  implicit val responseEventSemigroup: Semigroup[ResponseEvent] = (x: ResponseEvent, y: ResponseEvent) =>
    (x, y) match {
      case (x: InspectableActorsResponse, y: InspectableActorsResponse) =>
        InspectableActorsResponse(x.inspectableActors ++ y.inspectableActors)

      case (x: GroupsResponse, y: GroupsResponse) =>
        val xyGroups = (x.group, y.group) match {
          case (Left(_), y)            => y
          case (r @ Right(_), Left(_)) => r
          case (Right(r1), Right(r2))  => Right(r1 ++ r2)
        }
        GroupsResponse(xyGroups)

      case (x: GroupResponse, y: GroupResponse)             => GroupResponse(x.inspectableActors ++ y.inspectableActors)
      case (_: FragmentIdsResponse, y: FragmentIdsResponse) => y
      case (_: FragmentsResponse, y: FragmentsResponse)     => y
      case _                                                => y
  }
}

final case class InspectableActorsResponse(inspectableActors: List[String]) extends ResponseEvent {
  val toGRPC: grpc.InspectableActorsResponse = InspectableActorsResponse.grpcIso(this)
}

object InspectableActorsResponse {
  def fromGRPC(r: grpc.InspectableActorsResponse): InspectableActorsResponse = grpcIso.get(r)

  val grpcIso: Iso[grpc.InspectableActorsResponse, InspectableActorsResponse] =
    Iso[grpc.InspectableActorsResponse, InspectableActorsResponse](
      r => InspectableActorsResponse(r.inspectableActors.toList)
    )(r => grpc.InspectableActorsResponse(r.inspectableActors))
}

final case class GroupsResponse(group: Either[ActorNotInspectable, List[Group]]) extends ResponseEvent {
  val toGRPC: grpc.GroupsResponse = GroupsResponse.grpcPrism(this)
}

object GroupsResponse {
  def fromGRPC(r: grpc.GroupsResponse): Option[GroupsResponse] = grpcPrism.getOption(r)

  val grpcPrism: Prism[grpc.GroupsResponse, GroupsResponse] =
    Prism.partial[grpc.GroupsResponse, GroupsResponse] {
      case grpc.GroupsResponse(grpc.GroupsResponse.Res.Groups(grpc.GroupsResponse.Groups(groups))) =>
        GroupsResponse(Either.right(groups.map(Group).toList))
      case grpc.GroupsResponse(grpc.GroupsResponse.Res.Error(grpc.Error.ActorNotInspectable(id))) =>
        GroupsResponse(Either.left(ActorNotInspectable(id)))
    } {
      case GroupsResponse(Right(groups)) =>
        grpc.GroupsResponse(grpc.GroupsResponse.Res.Groups(grpc.GroupsResponse.Groups(groups.map(_.name))))
      case GroupsResponse(Left(ActorNotInspectable(id))) =>
        grpc.GroupsResponse(grpc.GroupsResponse.Res.Error(grpc.Error.ActorNotInspectable(id)))
    }
}

final case class GroupResponse(inspectableActors: List[String]) extends ResponseEvent {
  val toGRPC: grpc.GroupResponse = GroupResponse.grpcIso(this)
}

object GroupResponse {
  def fromGRPC(r: grpc.GroupResponse): GroupResponse = grpcIso.get(r)

  val grpcIso: Iso[grpc.GroupResponse, GroupResponse] =
    Iso[grpc.GroupResponse, GroupResponse](r => GroupResponse(r.inspectableActors.toList))(
      r => grpc.GroupResponse(r.inspectableActors)
    )
}

final case class FragmentIdsResponse(ids: Either[Error, (String, List[FragmentId])]) extends ResponseEvent {
  val toGRPC: grpc.FragmentIdsResponse = FragmentIdsResponse.grpcPrism(this)
}

object FragmentIdsResponse {
  def fromGRPC(r: grpc.FragmentIdsResponse): Option[FragmentIdsResponse] = grpcPrism.getOption(r)

  def grpcPrism: Prism[grpc.FragmentIdsResponse, FragmentIdsResponse] =
    Prism.partial[grpc.FragmentIdsResponse, FragmentIdsResponse] {
      case grpc.FragmentIdsResponse(
          grpc.FragmentIdsResponse.Res.FragmentIds(grpc.FragmentIdsResponse.FragmentIds(state, ids))
          ) =>
        FragmentIdsResponse(Either.right((state, ids.map(FragmentId).toList)))

      case grpc.FragmentIdsResponse(
          grpc.FragmentIdsResponse.Res
            .Error(grpc.Error(grpc.Error.Error.ActorNotInspectable(grpc.Error.ActorNotInspectable(id))))
          ) =>
        FragmentIdsResponse(Either.left(ActorNotInspectable(id)))

      case grpc.FragmentIdsResponse(
          grpc.FragmentIdsResponse.Res
            .Error(grpc.Error(grpc.Error.Error.UnreachableInspectableActor(grpc.Error.UnreachableInspectableActor(id))))
          ) =>
        FragmentIdsResponse(Either.left(UnreachableInspectableActor(id)))
    } {
      case FragmentIdsResponse(Right((state, fragmentIds))) =>
        grpc.FragmentIdsResponse(
          grpc.FragmentIdsResponse.Res.FragmentIds(grpc.FragmentIdsResponse.FragmentIds(state, fragmentIds.map(_.id)))
        )

      case FragmentIdsResponse(Left(ActorNotInspectable(id))) =>
        grpc.FragmentIdsResponse(
          grpc.FragmentIdsResponse.Res
            .Error(grpc.Error(grpc.Error.Error.ActorNotInspectable(grpc.Error.ActorNotInspectable(id))))
        )

      case FragmentIdsResponse(Left(UnreachableInspectableActor(id))) =>
        grpc.FragmentIdsResponse(
          grpc.FragmentIdsResponse.Res
            .Error(grpc.Error(grpc.Error.Error.UnreachableInspectableActor(grpc.Error.UnreachableInspectableActor(id))))
        )
    }
}

final case class FragmentsResponse(fragments: Either[Error, (String, Map[FragmentId, FinalizedFragment])])
    extends ResponseEvent {
  val toGRPC: grpc.FragmentsResponse = FragmentsResponse.grpcPrism(this)
}

object FragmentsResponse {
  def fromGRPC(r: grpc.FragmentsResponse): Option[FragmentsResponse] = grpcPrism.getOption(r)

  val grpcPrism: Prism[grpc.FragmentsResponse, FragmentsResponse] =
    Prism.partial[grpc.FragmentsResponse, FragmentsResponse] {
      case grpc.FragmentsResponse(
          grpc.FragmentsResponse.Res.Fragments(grpc.FragmentsResponse.Fragments(state, fragments))
          ) =>
        FragmentsResponse(Right((state, fragments.map {
          case (k, v) =>
            (FragmentId(k), RenderedFragment(v))
        })))
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
      case FragmentsResponse(Right((state, fragments))) =>
        grpc.FragmentsResponse(
          grpc.FragmentsResponse.Res.Fragments(
            grpc.FragmentsResponse.Fragments(
              state,
              fragments.map {
                case (k, UndefinedFragment)          => (k.id, "UNDEFINED")
                case (k, RenderedFragment(fragment)) => (k.id, fragment)
              }
            )
          )
        )
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
