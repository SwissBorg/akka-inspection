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

class ActorInspectorImpl(system: ActorSystem, actorInspectorManager: ActorRef) extends Extension {
  import ActorInspectorImpl._

  implicit val timer: Timeout = 10 seconds // TODO BEEEHHHH

  def put(ref: ActorRef, keys: Set[FragmentId], groups: Set[Group]): Unit =
    actorInspectorManager ! Put(InspectableActorRef(ref), keys, groups)

  def release(ref: ActorRef): Unit = actorInspectorManager ! Release(InspectableActorRef(ref))

  def requestInspectableActors(request: InspectableActorsRequest.type): Future[InspectableActorsResponse] =
    (actorInspectorManager ? request).mapTo[InspectableActorsResponse]

  def requestGroups(request: GroupsRequest): Future[GroupsResponse] =
    (actorInspectorManager ? request).mapTo[GroupsResponse]

  def requestFragmentIds(request: FragmentIdsRequest): Future[FragmentIdsResponse] =
    (actorInspectorManager ? request).mapTo[FragmentIdsResponse]

  def requestFragments(request: FragmentsRequest): Future[FragmentsResponse] =
    (actorInspectorManager ? request).mapTo[FragmentsResponse]
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
