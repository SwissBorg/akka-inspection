package akka.inspection.extension

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.inspection.manager.ActorInspectorManager
import akka.inspection.{ActorInspection, MutableActorInspection}
import com.typesafe.config.ConfigFactory

/**
 * Extension adding the possibility to inspect actors from outside the cluster.
 *
 * @see [[ActorInspection]] and [[MutableActorInspection]] to add the ability to inspect to an actor.
 */
object ActorInspector extends ExtensionId[ActorInspectorImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ActorInspectorImpl = {
    val actorInspectorManager = system.actorOf(ActorInspectorManager.props(), "inspector-manager")

    val impl: ActorInspectorImpl = new ActorInspectorImpl(system, actorInspectorManager)

    val conf = ConfigFactory.load()

    val _ = if (conf.getBoolean("akka.inspection.enable-server")) {
      new ActorInspectorServer(impl,
                               system,
                               conf.getString("akka.inspection.server.hostname"),
                               conf.getInt("akka.inspection.server.port")).run()
    }

    impl
  }

  override def lookup(): ExtensionId[_ <: Extension] = ActorInspector
}
