package akka.inspection

import akka.actor.{ActorRef, ActorSystem, Extension, Scheduler}
import akka.inspection.ActorInspection.FragmentId
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

  def put(ref: ActorRef, keys: Set[FragmentId], groups: Set[Group]): Unit =
    actorInspectorManager ! Put(InspectableActorRef(ref), keys, groups)

  def release(ref: ActorRef): Unit = actorInspectorManager ! Release(InspectableActorRef(ref))

  // TODO should not be here. Should not be called from within an actor.
  override def requestQueryableActors(in: grpc.InspectableActorsRequest): Future[grpc.InspectableActorsResponse] = {
    import scala.concurrent.ExecutionContext.Implicits.global // TODO

    implicit val timer: Timeout = 10 seconds // TODO BEEEHHHH
    implicit val scheduler: Scheduler = system.scheduler

    (actorInspectorManager ? InspectableActorsRequest).mapTo[InspectableActorsResponse].map(_.toGRPC)
  }

  // TODO should not be here. Should not be called from within an actor.
  override def requestGroups(in: grpc.GroupsRequest): Future[grpc.GroupsResponse] = {
    import scala.concurrent.ExecutionContext.Implicits.global // TODO

    implicit val timer: Timeout = 10 seconds // TODO BEEEHHHH
    implicit val scheduler: Scheduler = system.scheduler

    (actorInspectorManager ? GroupsRequest.fromGRPC(in)).mapTo[GroupsResponse].map(_.toGRPC)
  }

  override def requestFragmentIds(in: grpc.FragmentIdsRequest): Future[grpc.FragmentIdsResponse] = {
    import scala.concurrent.ExecutionContext.Implicits.global // TODO

    implicit val timer: Timeout = 10 seconds // TODO BEEEHHHH
    implicit val scheduler: Scheduler = system.scheduler

    (actorInspectorManager ? FragmentIdsRequest.fromGRPC(in)).mapTo[FragmentIdsResponse].map(_.toGRPC)
  }

  override def requestFragments(in: grpc.FragmentsRequest): Future[grpc.FragmentsResponse] = {
    implicit val timer: Timeout = 10 seconds // TODO BEEEHHHH
    implicit val scheduler: Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    (actorInspectorManager ? FragmentsRequest.fromGRPC(in)).mapTo[FragmentsResponse].map(_.toGRPC)
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
