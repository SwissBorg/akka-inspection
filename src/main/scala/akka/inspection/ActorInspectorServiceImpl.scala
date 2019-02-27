package akka.inspection

import akka.actor.ActorSystem
import akka.inspection.manager.ActorInspectorManager._
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.{ExecutionContext, Future}

class ActorInspectorServiceImpl(system: ActorSystem) extends grpc.ActorInspectionService {
  private val manager = ActorInspector(system)

  implicit private val sys: ActorSystem = system
  implicit private val mat: Materializer = ActorMaterializer()
  implicit private val ec: ExecutionContext = sys.dispatcher

  override def requestInspectableActors(in: grpc.InspectableActorsRequest): Future[grpc.InspectableActorsResponse] =
    manager.requestInspectableActors(InspectableActorsRequest.fromGRPC(in)).map(_.toGRPC)

  override def requestGroups(in: grpc.GroupsRequest): Future[grpc.GroupsResponse] =
    manager.requestGroups(GroupsRequest.fromGRPC(in)).map(_.toGRPC)

  override def requestGroup(
    in: grpc.GroupRequest
  ): Future[grpc.GroupResponse] = manager.requestGroup(GroupRequest.fromGRPC(in)).map(_.toGRPC)

  override def requestFragmentIds(in: grpc.FragmentIdsRequest): Future[grpc.FragmentIdsResponse] =
    manager.requestFragmentIds(FragmentIdsRequest.fromGRPC(in)).map(_.toGRPC)

  override def requestFragments(in: grpc.FragmentsRequest): Future[grpc.FragmentsResponse] =
    manager.requestFragments(FragmentsRequest.fromGRPC(in)).map(_.toGRPC)
}
