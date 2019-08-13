package akka.inspection.extension

import akka.Done
import akka.actor.{CoordinatedShutdown, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.inspection.manager.ActorInspectorManager
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Extension adding the possibility to inspect actors from outside the cluster.
  *
  * @see `ActorInspection` and `MutableActorInspection` to add the ability to inspect to an actor.
  */
object ActorInspector extends ExtensionId[ActorInspectorImpl] with ExtensionIdProvider with StrictLogging {
  override def createExtension(system: ExtendedActorSystem): ActorInspectorImpl = {
    logger.info("Starting ActorInspector...")

    val actorInspectorManager = system.actorOf(ActorInspectorManager.props(), "inspector-manager")

    val impl: ActorInspectorImpl = new ActorInspectorImpl(system, actorInspectorManager)

    val conf = system.settings.config

    val _ = if (conf.getBoolean("akka.inspection.enable-server")) {
      val bind = new ActorInspectorServer(
        impl,
        system,
        conf.getString("akka.inspection.server.hostname"),
        conf.getInt("akka.inspection.server.port")
      ).run()

      CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseServiceUnbind, "unbind-inspector-server") { () =>
        for {
          bind <- bind
          _    <- bind.terminate(10.seconds)
        } yield Done
      }
    }

    impl
  }

  override def lookup(): ExtensionId[_ <: Extension] = ActorInspector
}
