package akka.inspection
import akka.actor.{ActorRef, ActorSystem, Scheduler}
import akka.inspection.ActorInspectorManager.Groups.Group
import akka.inspection.ActorInspectorManager.Keys.Key
import akka.inspection.ActorInspectorManager._
import akka.pattern.ask
import akka.util.Timeout
import akka.{actor => untyped}

import scala.concurrent.Future
import scala.concurrent.duration._

class ActorInspectorImpl(system: ActorSystem, actorInspectorManager: ActorRef) extends untyped.Extension with grpc.ActorInspectionService {
  def put(ref: untyped.ActorRef, keys: Set[Key], groups: Set[Group]): Unit = actorInspectorManager ! Put(ref, keys, groups)
  def release(ref: untyped.ActorRef): Unit                                 = actorInspectorManager ! Release(ref)

  // TODO should not be here. Should not be called from within an actor.
  override def requestQueryableActors(in: grpc.QueryableActorsRequest): Future[grpc.QueryableActorsResponse] = {
    import scala.concurrent.ExecutionContext.Implicits.global // TODO

    implicit val timer: Timeout       = 10 seconds // TODO BEEEHHHH
    implicit val scheduler: Scheduler = system.scheduler

    val f: Future[QueryableActorsResponse] = (actorInspectorManager ? QueryableActorsRequest).mapTo[QueryableActorsResponse]
    f.map(r => grpc.QueryableActorsResponse(r.queryable.toSeq))
  }

  // TODO should not be here. Should not be called from within an actor.
  override def requestActorGroups(in: grpc.ActorGroupsRequest): Future[grpc.ActorGroupsResponse] = {
    import scala.concurrent.ExecutionContext.Implicits.global // TODO

    implicit val timer: Timeout       = 10 seconds // TODO BEEEHHHH
    implicit val scheduler: Scheduler = system.scheduler

    val f: Future[ActorGroupsResponse] = (actorInspectorManager ? ActorGroupsRequest(in.actor)).mapTo[ActorGroupsResponse]
    f.map {
      case ActorGroupsResponse(Right(groups)) =>
        grpc.ActorGroupsResponse(grpc.ActorGroupsResponse.GroupsRes.Success(grpc.ActorGroupsResponse.Groups(groups.toSeq.map(_.name))))
      case ActorGroupsResponse(Left(ActorNotInspectable)) =>
        grpc.ActorGroupsResponse(grpc.ActorGroupsResponse.GroupsRes.Failure(ActorNotInspectable.toString)) // TODO BEEEHHHhhh

    }
  }

  override def requestActorKeys(in: grpc.ActorKeysRequest): Future[grpc.ActorKeysResponse] = {
    import scala.concurrent.ExecutionContext.Implicits.global // TODO

    implicit val timer: Timeout       = 10 seconds // TODO BEEEHHHH
    implicit val scheduler: Scheduler = system.scheduler

    val f: Future[ActorKeysResponse] = (actorInspectorManager ? ActorGroupsRequest(in.actor)).mapTo[ActorKeysResponse]
    f.map {
      case ActorKeysResponse(Right(keys)) =>
        grpc.ActorKeysResponse(grpc.ActorKeysResponse.KeysRes.Success(grpc.ActorKeysResponse.Keys(keys.toSeq.map(_.value))))
      case ActorKeysResponse(Left(ActorNotInspectable)) =>
        grpc.ActorKeysResponse(grpc.ActorKeysResponse.KeysRes.Failure(ActorNotInspectable.toString)) // TODO BEEEHHHhhh
    }
  }
}
