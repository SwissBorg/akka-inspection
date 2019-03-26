package akka.inspection.extension

import akka.actor.{ActorRef, ActorSystem, Extension}
import akka.inspection.grpc
import akka.inspection.manager.ActorInspectorManager.InspectableActorRef
import akka.inspection.manager._
import akka.inspection.manager.state.Group
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

private[extension] class ActorInspectorImpl(system: ActorSystem, actorInspectorManager: ActorRef)
    extends Extension
    with grpc.ActorInspectionService {

  implicit val timer: Timeout       = 10 seconds // TODO BEEEHHHH
  implicit val ec: ExecutionContext = system.getDispatcher

  def subscribe(ref: ActorRef, groups: Set[Group]): Unit =
    actorInspectorManager ! Subscribe(InspectableActorRef(ref), groups)

  def unsubscribe(ref: ActorRef): Unit = actorInspectorManager ! Unsubscribe(InspectableActorRef(ref))

  override def requestInspectableActors(in: grpc.InspectableActorsRequest): Future[grpc.InspectableActorsResponse] =
    (actorInspectorManager ? InspectableActorsRequest.fromGRPC(in))
      .mapTo[InspectableActorsResponse]
      .transform(_.map(_.toGRPC))

  override def requestGroups(in: grpc.GroupsRequest): Future[grpc.GroupsResponse] =
    (actorInspectorManager ? GroupsRequest.fromGRPC(in)).mapTo[GroupsResponse].transform(_.map(_.toGRPC))

  override def requestGroup(in: grpc.GroupRequest): Future[grpc.GroupResponse] =
    (actorInspectorManager ? GroupRequest.fromGRPC(in)).mapTo[GroupResponse].transform(_.map(_.toGRPC))

  override def requestFragmentIds(in: grpc.FragmentIdsRequest): Future[grpc.FragmentIdsResponse] =
    (actorInspectorManager ? FragmentIdsRequest.fromGRPC(in)).mapTo[FragmentIdsResponse].transform(_.map(_.toGRPC))

  override def requestFragments(in: grpc.FragmentsRequest): Future[grpc.FragmentsResponse] =
    (actorInspectorManager ? FragmentsRequest.fromGRPC(in)).mapTo[FragmentsResponse].transform(_.map(_.toGRPC))
}
