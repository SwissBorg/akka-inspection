package akka.inspection
import akka.actor.{ActorRef, Extension}
import akka.inspection.ActorInspectorImpl.Group

class ActorInspectorImpl extends Extension {
  def put(ref: ActorRef, keys: Set[String], group: Group): Unit = ???
  def release(ref: ActorRef): Unit                              = ???
}

object ActorInspectorImpl {
  sealed abstract class Group extends Product with Serializable
  object Group {
    final case class Name(name: String) extends Group
    final case object None              extends Group
  }
}
