package akka.inspection

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.inspection.manager.ActorInspectorManager
import akka.inspection.server.ActorInspectorServer

object ActorInspector extends ExtensionId[ActorInspectorImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ActorInspectorImpl = {
    // Start the singleton manager
    val actorInspectorManager = system.actorOf(ActorInspectorManager.props(), "manager")

//    // Start server
//    new ActorInspectorServer(system).run()

    new ActorInspectorImpl(system, actorInspectorManager)
  }

  override def lookup(): ExtensionId[_ <: Extension] = ActorInspector
}
