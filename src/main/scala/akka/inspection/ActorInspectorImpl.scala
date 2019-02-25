package akka.inspection

import akka.actor.{ActorRef, ActorSystem, Extension, Scheduler}
import akka.inspection.ActorInspection.{StateFragmentRequest, StateFragmentResponse}
import akka.inspection.ActorInspectorManager.Groups.Group
import akka.inspection.ActorInspectorManager.StateFragments.StateFragmentId
import akka.inspection.ActorInspectorManager._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
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
      case ActorGroupsResponse(Left(ActorNotInspectable)) =>
        grpc.ActorGroupsResponse(grpc.ActorGroupsResponse.GroupsRes.Failure(ActorNotInspectable.toString)) // TODO BEEEHHHhhh

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
      case StateFragmentIdsResponse(Left(ActorNotInspectable)) =>
        grpc.ActorKeysResponse(grpc.ActorKeysResponse.KeysRes.Failure(ActorNotInspectable.toString)) // TODO BEEEHHHhhh
    }
  }

  def f(in: StateFragmentRequest): Future[StateFragmentResponse] = {
    implicit val timer: Timeout = 10 seconds // TODO BEEEHHHH
    implicit val scheduler: Scheduler = system.scheduler

    (actorInspectorManager ? in).mapTo[Either[ActorInspectorManager.ActorNotInspectable.type, StateFragmentResponse]]

    ???
  }
}

object ActorInspectorImpl {

  /**
   * An [[ActorRef]] that can be inspected.
   */
  sealed abstract case class InspectableActorRef(ref: ActorRef)
  object InspectableActorRef {
    private[inspection] def apply(ref: ActorRef): InspectableActorRef = new InspectableActorRef(ref) {}

    sealed abstract class BackPressureSignal extends Product with Serializable
    final case object Init extends BackPressureSignal
    final case object Ack extends BackPressureSignal
    final case object Complete extends BackPressureSignal
  }
}
