package akka.inspection
import akka.actor.typed.ActorRef
import akka.inspection.ActorInspectorImpl.Group
import akka.inspection.typed.ActorInspectorManager.{Events, Put, PutWithoutGroup, Release}
import akka.{actor => untyped}

class ActorInspectorImpl(actorInspectorManager: ActorRef[Events]) extends untyped.Extension {
  def put(ref: untyped.ActorRef, keys: Set[String], group: Group): Unit = group match {
    case Group.Name(n) => actorInspectorManager ! Put(ref, keys, n)
    case Group.None    => actorInspectorManager ! PutWithoutGroup(ref, keys)
  }

  def release(ref: untyped.ActorRef): Unit = actorInspectorManager ! Release(ref)
}

object ActorInspectorImpl {
  sealed abstract class Group extends Product with Serializable
  object Group {
    final case class Name(name: String) extends Group
    final case object None              extends Group
  }
}
