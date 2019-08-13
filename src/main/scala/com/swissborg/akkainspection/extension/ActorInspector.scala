package com.swissborg.akkainspection.extension

import akka.Done
import akka.actor.{CoordinatedShutdown, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.swissborg.akkainspection.manager.ActorInspectorManager
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

    val actorInspectorManager =
      system.actorOf(ActorInspectorManager.props(), "inspector-manager")

    val impl: ActorInspectorImpl =
      new ActorInspectorImpl(system, actorInspectorManager)

    val conf = system.settings.config

    val _ = if (conf.getBoolean("com.swissborg.akkainspection.enable-server")) {
      val bind = new ActorInspectorServer(
        impl,
        system,
        conf.getString("com.swissborg.akkainspection.server.hostname"),
        conf.getInt("com.swissborg.akkainspection.server.port")
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
