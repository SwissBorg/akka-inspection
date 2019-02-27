package akka.inspection.manager.state

private[manager] trait Errors {
  sealed abstract class Error extends Product with Serializable
  final case class ActorNotInspectable(id: String) extends Error
  final case class UnreachableInspectableActor(id: String) extends Error
}
