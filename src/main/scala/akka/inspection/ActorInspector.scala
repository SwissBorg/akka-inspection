package akka.inspection
import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}

object ActorInspector extends ExtensionId[ActorInspectorImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ActorInspectorImpl = new ActorInspectorImpl
  override def lookup(): ExtensionId[_ <: Extension]                           = ActorInspector
}
