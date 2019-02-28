package akka.inspection

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.inspection.manager.ActorInspectorManager
import akka.inspection.server.ActorInspectorServer
import com.typesafe.config.ConfigFactory

object ActorInspector extends ExtensionId[ActorInspectorImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ActorInspectorImpl = {
    val actorInspectorManager = system.actorOf(ActorInspectorManager.props(), "inspector-manager")

    val impl: ActorInspectorImpl = new ActorInspectorImpl(system, actorInspectorManager)

    val conf = ConfigFactory.defaultApplication()

    // Start server
//    new ActorInspectorServer(impl,
//                             system,
//                             conf.getString("akka.inspection.server.hostname"),
//                             conf.getInt("akka.inspection.server.port")).run()

    impl
  }

  override def lookup(): ExtensionId[_ <: Extension] = ActorInspector
}
