package akka.inspection

import akka.actor.{ActorRef, ActorSystem, Extension, Scheduler}
import akka.inspection.ActorInspection.FragmentId
import akka.inspection.ActorInspectorManager.Groups.Group
import akka.inspection.ActorInspectorManager._
import akka.pattern.ask
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class ActorInspectorServiceImpl(system: ActorSystem) extends grpc.ActorInspectionService {
  private val manager = ActorInspector(system)

  implicit val sys: ActorSystem = system
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = sys.dispatcher
  implicit val timer: Timeout = 10 seconds // TODO BEEEHHHH

  override def requestInspectableActors(in: grpc.InspectableActorsRequest): Future[grpc.InspectableActorsResponse] =
    manager.requestInspectableActors(in)

  override def requestGroups(in: grpc.GroupsRequest): Future[grpc.GroupsResponse] = manager.requestGroups(in)

  override def requestFragmentIds(in: grpc.FragmentIdsRequest): Future[grpc.FragmentIdsResponse] =
    manager.requestFragmentIds(in)

  override def requestFragments(in: grpc.FragmentsRequest): Future[grpc.FragmentsResponse] =
    manager.requestFragments(in)
}

class ActorInspectorImpl(system: ActorSystem, actorInspectorManager: ActorRef) extends Extension {
  import ActorInspectorImpl._

  implicit val sys: ActorSystem = system
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = sys.dispatcher
  implicit val timer: Timeout = 10 seconds // TODO BEEEHHHH

  def put(ref: ActorRef, keys: Set[FragmentId], groups: Set[Group]): Unit =
    actorInspectorManager ! Put(InspectableActorRef(ref), keys, groups)

  def release(ref: ActorRef): Unit = actorInspectorManager ! Release(InspectableActorRef(ref))

  def requestInspectableActors(in: grpc.InspectableActorsRequest): Future[grpc.InspectableActorsResponse] =
    (actorInspectorManager ? InspectableActorsRequest).mapTo[InspectableActorsResponse].map(_.toGRPC)

  def requestGroups(in: grpc.GroupsRequest): Future[grpc.GroupsResponse] =
    (actorInspectorManager ? GroupsRequest.fromGRPC(in)).mapTo[GroupsResponse].map(_.toGRPC)

  def requestFragmentIds(in: grpc.FragmentIdsRequest): Future[grpc.FragmentIdsResponse] =
    (actorInspectorManager ? FragmentIdsRequest.fromGRPC(in)).mapTo[FragmentIdsResponse].map(_.toGRPC)

  def requestFragments(in: grpc.FragmentsRequest): Future[grpc.FragmentsResponse] =
    (actorInspectorManager ? FragmentsRequest.fromGRPC(in)).mapTo[FragmentsResponse].map(_.toGRPC)
}

object ActorInspectorImpl {

  /**
   * An [[ActorRef]] that can be inspected.
   */
  sealed abstract case class InspectableActorRef(ref: ActorRef) {
    val toId: String = ref.path.toString // TODO render?
  }

  object InspectableActorRef {
    private[inspection] def apply(ref: ActorRef): InspectableActorRef = new InspectableActorRef(ref) {}

    sealed abstract class BackPressureSignal extends Product with Serializable
    final case object Init extends BackPressureSignal
    final case object Ack extends BackPressureSignal
    final case object Complete extends BackPressureSignal
  }
}
