package akka.inspection

import akka.actor.{ActorRef, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, PoisonPill, Props}
import akka.cluster.singleton.{
  ClusterSingletonManager,
  ClusterSingletonManagerSettings,
  ClusterSingletonProxy,
  ClusterSingletonProxySettings
}
import akka.inspection.manager.ActorInspectorManager
import akka.inspection.server.ActorInspectorServer

object ActorInspector extends ExtensionId[ActorInspectorImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ActorInspectorImpl = {
    // Start the singleton manager
    system.actorOf(
      ClusterSingletonManager.props(singletonProps = Props(classOf[ActorInspectorManager]),
                                    terminationMessage = PoisonPill,
                                    settings = ClusterSingletonManagerSettings(system)),
      name = "ActorInspectorManager"
    )

    val proxy: ActorRef =
      system.actorOf(
        ClusterSingletonProxy.props(singletonManagerPath = "/user/ActorInspectorManager",
                                    settings = ClusterSingletonProxySettings(system))
      )

    val actorInspectorManager = system.actorOf(ActorInspectorManager.props())

    // Start server
    new ActorInspectorServer(system).run()

    new ActorInspectorImpl(system, actorInspectorManager)
  }

  override def lookup(): ExtensionId[_ <: Extension] = ActorInspector
}
