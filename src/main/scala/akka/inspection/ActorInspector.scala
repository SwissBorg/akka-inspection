package akka.inspection
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, SupervisorStrategy}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import akka.inspection.typed.ActorInspectorManager
import akka.inspection.typed.ActorInspectorManager.{State, SubscriptionCommand}
import akka.{actor => untyped}

object ActorInspector extends untyped.ExtensionId[ActorInspectorImpl] with untyped.ExtensionIdProvider {
  override def createExtension(system: untyped.ExtendedActorSystem): ActorInspectorImpl = {
    val typedSystem: ActorSystem[Nothing] = ActorSystem.wrap(system)

    val singletonManager = ClusterSingleton(typedSystem)
    val proxy: ActorRef[SubscriptionCommand] = singletonManager.init(
      SingletonActor[SubscriptionCommand](Behaviors.supervise(ActorInspectorManager.mainBehavior(State.empty)).onFailure(SupervisorStrategy.restart),
                                          "ActorInspectorManager"))

    new ActorInspectorImpl(proxy)
  }

  override def lookup(): untyped.ExtensionId[_ <: untyped.Extension] = ActorInspector
}
