package akka.inspection

import akka.actor.{ActorRef, ActorSystem, Extension}
import akka.inspection.ActorInspection.FragmentId
import akka.inspection.manager.ActorInspectorManager.InspectableActorRef
import akka.inspection.manager._
import akka.inspection.manager.state.Group
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ActorInspectorImpl(system: ActorSystem, actorInspectorManager: ActorRef)
    extends Extension
    with grpc.ActorInspectionService {
  implicit val timer: Timeout = 10 seconds // TODO BEEEHHHH
  implicit val ec: ExecutionContext = system.getDispatcher

  def put(ref: ActorRef, keys: Set[FragmentId], groups: Set[Group]): Unit =
    actorInspectorManager ! Put(InspectableActorRef(ref), keys, groups)

  def release(ref: ActorRef): Unit = actorInspectorManager ! Release(InspectableActorRef(ref))

  override def requestInspectableActors(in: grpc.InspectableActorsRequest): Future[grpc.InspectableActorsResponse] =
    (actorInspectorManager ? InspectableActorsRequest.fromGRPC(in)).mapTo[InspectableActorsResponse].map(_.toGRPC)

  override def requestGroups(in: grpc.GroupsRequest): Future[grpc.GroupsResponse] =
    (actorInspectorManager ? GroupsRequest.fromGRPC(in)).mapTo[GroupsResponse].map(_.toGRPC)

  override def requestGroup(in: grpc.GroupRequest): Future[grpc.GroupResponse] =
    (actorInspectorManager ? GroupRequest.fromGRPC(in)).mapTo[GroupResponse].map(_.toGRPC)

  override def requestFragmentIds(in: grpc.FragmentIdsRequest): Future[grpc.FragmentIdsResponse] =
    (actorInspectorManager ? FragmentIdsRequest.fromGRPC(in)).mapTo[FragmentIdsResponse].map(_.toGRPC)

  override def requestFragments(in: grpc.FragmentsRequest): Future[grpc.FragmentsResponse] =
    (actorInspectorManager ? FragmentsRequest.fromGRPC(in)).mapTo[FragmentsResponse].map(_.toGRPC)

  override def requestAllFragments(in: grpc.AllFragmentsRequest): Future[grpc.FragmentsResponse] =
    (actorInspectorManager ? AllFragmentsRequest.fromGRPC(in)).mapTo[FragmentsResponse].map(_.toGRPC)
}
