package akka.inspection

import akka.actor.{ActorRef, ActorSystem, Extension, Scheduler}
import akka.inspection.ActorInspection.StateFragmentId
import akka.inspection.ActorInspectorManager.Groups.Group
import akka.inspection.ActorInspectorManager._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class ActorInspectorImpl(system: ActorSystem, actorInspectorManager: ActorRef)
    extends Extension
    with grpc.ActorInspectionService {
  import ActorInspectorImpl._

  def put(ref: ActorRef, keys: Set[StateFragmentId], groups: Set[Group]): Unit =
    actorInspectorManager ! Put(InspectableActorRef(ref), keys, groups)

  def release(ref: ActorRef): Unit = actorInspectorManager ! Release(InspectableActorRef(ref))

  // TODO should not be here. Should not be called from within an actor.
  override def requestQueryableActors(in: grpc.QueryableActorsRequest): Future[grpc.QueryableActorsResponse] = {
    import scala.concurrent.ExecutionContext.Implicits.global // TODO

    implicit val timer: Timeout = 10 seconds // TODO BEEEHHHH
    implicit val scheduler: Scheduler = system.scheduler

    val f: Future[InspectableActorsResponse] =
      (actorInspectorManager ? InspectableActorsRequest).mapTo[InspectableActorsResponse]
    f.map(r => grpc.QueryableActorsResponse(r.queryable.toSeq))
  }

  // TODO should not be here. Should not be called from within an actor.
  override def requestActorGroups(in: grpc.ActorGroupsRequest): Future[grpc.ActorGroupsResponse] = {
    import scala.concurrent.ExecutionContext.Implicits.global // TODO

    implicit val timer: Timeout = 10 seconds // TODO BEEEHHHH
    implicit val scheduler: Scheduler = system.scheduler

    val f: Future[ActorGroupsResponse] =
      (actorInspectorManager ? ActorGroupsRequest(in.actor)).mapTo[ActorGroupsResponse]
    f.map {
      case ActorGroupsResponse(Right(groups)) =>
        grpc.ActorGroupsResponse(
          grpc.ActorGroupsResponse.GroupsRes.Success(grpc.ActorGroupsResponse.Groups(groups.toSeq.map(_.name)))
        )

      case ActorGroupsResponse(Left(err)) =>
        grpc.ActorGroupsResponse(grpc.ActorGroupsResponse.GroupsRes.Failure(err.toString)) // TODO BEEEHHHhhh

    }
  }

  override def requestActorKeys(in: grpc.ActorKeysRequest): Future[grpc.ActorKeysResponse] = {
    import scala.concurrent.ExecutionContext.Implicits.global // TODO

    implicit val timer: Timeout = 10 seconds // TODO BEEEHHHH
    implicit val scheduler: Scheduler = system.scheduler

    val f: Future[StateFragmentIdsResponse] =
      (actorInspectorManager ? ActorGroupsRequest(in.actor)).mapTo[StateFragmentIdsResponse]
    f.map {
      case StateFragmentIdsResponse(Right(keys)) =>
        grpc.ActorKeysResponse(
          grpc.ActorKeysResponse.KeysRes.Success(grpc.ActorKeysResponse.Keys(keys.toSeq.map(_.id)))
        )

      case StateFragmentIdsResponse(Left(err)) =>
        grpc.ActorKeysResponse(grpc.ActorKeysResponse.KeysRes.Failure(err.toString)) // TODO BEEEHHHhhh
    }
  }

  override def requestStateFragments(
    in: grpc.StateFragmentsRequest
  ): Future[grpc.StateFragmentsResponse] = in match {
    case grpc.StateFragmentsRequest(_, actor, fragmentIds) =>
      implicit val timer: Timeout = 10 seconds // TODO BEEEHHHH
      implicit val scheduler: Scheduler = system.scheduler
      implicit val ec: ExecutionContext = system.dispatcher

      (actorInspectorManager ? StateFragmentsRequest(fragmentIds.map(StateFragmentId).toSet, actor))
        .mapTo[StateFragmentsResponse]
        .map {
          case StateFragmentsResponse.Success(fragments) =>
            grpc.StateFragmentsResponse(
              grpc.StateFragmentsResponse.FragmentsRes.Success(grpc.StateFragmentsResponse.Fragments(fragments.map {
                case (k, v) => (k.id, v.toString)
              }))
            )
          case StateFragmentsResponse.Failure(error) =>
            grpc.StateFragmentsResponse(
              grpc.StateFragmentsResponse.FragmentsRes.Failure(error.toString)
            )
        }
  }
}

object ActorInspectorImpl {

  /**
   * An [[ActorRef]] that can be inspected.
   */
  sealed abstract case class InspectableActorRef(ref: ActorRef) {
    val toId: String = ref.path.address.toString
  }

  object InspectableActorRef {
    private[inspection] def apply(ref: ActorRef): InspectableActorRef = new InspectableActorRef(ref) {}

    sealed abstract class BackPressureSignal extends Product with Serializable
    final case object Init extends BackPressureSignal
    final case object Ack extends BackPressureSignal
    final case object Complete extends BackPressureSignal
  }
}
