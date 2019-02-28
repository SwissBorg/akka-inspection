package akka.inspection.service

import akka.inspection.manager._
import akka.inspection.{grpc, ActorInspector, ActorInspectorImpl}
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}

/**
 * Service wrapping the [[ActorInspector]].
 */
class ActorInspectionServiceImpl(manager: ActorInspectorImpl)(implicit mat: Materializer, ec: ExecutionContext)
    extends grpc.ActorInspectionService {
  override def requestInspectableActors(in: grpc.InspectableActorsRequest): Future[grpc.InspectableActorsResponse] =
    manager.requestInspectableActors(InspectableActorsRequest.fromGRPC(in)).map(_.toGRPC)

  override def requestGroups(in: grpc.GroupsRequest): Future[grpc.GroupsResponse] =
    manager.requestGroups(GroupsRequest.fromGRPC(in)).map(_.toGRPC)

  override def requestGroup(in: grpc.GroupRequest): Future[grpc.GroupResponse] =
    manager.requestGroup(GroupRequest.fromGRPC(in)).map(_.toGRPC)

  override def requestFragmentIds(in: grpc.FragmentIdsRequest): Future[grpc.FragmentIdsResponse] =
    manager.requestFragmentIds(FragmentIdsRequest.fromGRPC(in)).map(_.toGRPC)

  override def requestFragments(in: grpc.FragmentsRequest): Future[grpc.FragmentsResponse] =
    manager.requestFragments(FragmentsRequest.fromGRPC(in)).map(_.toGRPC)
}
