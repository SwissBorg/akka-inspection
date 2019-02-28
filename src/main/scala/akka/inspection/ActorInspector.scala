package akka.inspection

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.inspection.manager.ActorInspectorManager
import akka.inspection.server.ActorInspectorServer
import com.typesafe.config.ConfigFactory

object ActorInspector extends ExtensionId[ActorInspectorImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ActorInspectorImpl = {
    // Start the singleton manager
    val actorInspectorManager = system.actorOf(ActorInspectorManager.props(), "manager")

//    val conf = ConfigFactory
//      .parseString("akka.http.server.preview.enable-http2 = on")
//      .withFallback(ConfigFactory.defaultApplication())

    val impl: ActorInspectorImpl = new ActorInspectorImpl(system, actorInspectorManager)

    //    // Start server
    new ActorInspectorServer(impl, system).run()
    impl
  }

  override def lookup(): ExtensionId[_ <: Extension] = ActorInspector
}
