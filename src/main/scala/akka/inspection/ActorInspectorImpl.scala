package akka.inspection
import akka.actor.{ActorRef, ActorSystem, Scheduler}
import akka.inspection.ActorInspectorImpl.Group
import akka.inspection.ActorInspectorManager._
import akka.pattern.ask
import akka.util.Timeout
import akka.{actor => untyped}

import scala.concurrent.Future
import scala.concurrent.duration._

class ActorInspectorImpl(system: ActorSystem, actorInspectorManager: ActorRef) extends untyped.Extension with akka.inspection.grpc.ActorInspectionService {
  def put(ref: untyped.ActorRef, keys: Set[String], group: Group): Unit = group match {
    case Group.Name(n) => actorInspectorManager ! Put(ref, keys, n)
    case Group.None    => actorInspectorManager ! PutWithoutGroup(ref, keys)
  }

  def release(ref: untyped.ActorRef): Unit = actorInspectorManager ! Release(ref)

  // TODO should not be here. Should not be called from within an actor.
  override def requestQueryableActors(in: grpc.QueryableActorsRequest): Future[grpc.QueryableActorsResponse] = {
    import scala.concurrent.ExecutionContext.Implicits.global // TODO

    implicit val timer: Timeout       = 10 seconds // TODO BEEEHHHH
    implicit val scheduler: Scheduler = system.scheduler

    val f: Future[QueryableActorsResponse] = (actorInspectorManager ? QueryableActorsRequest).mapTo[QueryableActorsResponse]
    f.map(r => grpc.QueryableActorsResponse(r.queryable))
  }

  // TODO should not be here. Should not be called from within an actor.
  override def requestActorGroup(in: grpc.ActorGroupRequest): Future[grpc.ActorGroupResponse] = {
    import scala.concurrent.ExecutionContext.Implicits.global // TODO

    implicit val timer: Timeout       = 10 seconds // TODO BEEEHHHH
    implicit val scheduler: Scheduler = system.scheduler

    val f: Future[ActorGroupResponse] = (actorInspectorManager ? ActorGroupRequest(in.actor)).mapTo[ActorGroupResponse]
    f.map {
      case ActorGroupResponse(Right(group))        => grpc.ActorGroupResponse(grpc.ActorGroupResponse.Group.GroupSome(group.name))
      case ActorGroupResponse(Left(GroupNotFound)) => grpc.ActorGroupResponse(grpc.ActorGroupResponse.Group.GroupNone(true))
      case ActorGroupResponse(Left(err))           => grpc.ActorGroupResponse(grpc.ActorGroupResponse.Group.GroupError(err.toString)) // TODO NOT USE TO STRING...
    }
  }
}

object ActorInspectorImpl {
  sealed abstract class Group extends Product with Serializable
  object Group {
    final case class Name(name: String) extends Group
    final case object None              extends Group
  }
}
