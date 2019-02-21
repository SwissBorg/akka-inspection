package akka.inspection

import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, SupervisorStrategy}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import akka.inspection.typed.ActorInspectorManager
import akka.inspection.typed.ActorInspectorManager.Events
import akka.{actor => untyped}

object ActorInspector extends untyped.ExtensionId[ActorInspectorImpl] with untyped.ExtensionIdProvider {
  override def createExtension(system: untyped.ExtendedActorSystem): ActorInspectorImpl = {
    val typedSystem: ActorSystem[Nothing] = ActorSystem.wrap(system)

    val singletonManager = ClusterSingleton(typedSystem)
    val proxy: ActorRef[Events] = singletonManager.init(
      SingletonActor[Events](Behaviors.supervise(ActorInspectorManager.Events.init).onFailure(SupervisorStrategy.restart), "ActorInspectorManager"))

    new ActorInspectorImpl(typedSystem, proxy)
  }

  override def lookup(): untyped.ExtensionId[_ <: untyped.Extension] = ActorInspector
}
