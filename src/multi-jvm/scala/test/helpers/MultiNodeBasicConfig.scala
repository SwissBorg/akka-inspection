package test.helpers

import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.{Config, ConfigFactory}

object MultiNodeBasicConfig extends MultiNodeConfig {
  val node1: RoleName = role("node1")
  val node2: RoleName = role("node2")
  val node3: RoleName = role("node3")

  val nodeList = List(node1, node2, node3)

//  nodeConfig(node1)(
//    ConfigFactory.parseString("""
//                                |akka {
//                                |  cluster {
//                                |    seed-nodes = ["akka.tcp://MutableActorInspectionSpec@127.0.0.1:2551"]
//                                |
//                                |    # auto downing is NOT safe for production deployments.
//                                |    # you may want to use it during development, read more about it in the docs.
//                                |    auto-down-unreachable-after = 10s
//                                |  }
//                                |}
//    """.stripMargin)
//  )
//
//  nodeConfig(node2)(
//    ConfigFactory.parseString("""
//                                |akka {
//                                |  cluster {
//                                |    seed-nodes = ["akka.tcp://MutableActorInspectionSpec@127.0.0.1:2552"]
//                                |
//                                |    # auto downing is NOT safe for production deployments.
//                                |    # you may want to use it during development, read more about it in the docs.
//                                |    auto-down-unreachable-after = 10s
//                                |  }
//                                |}
//    """.stripMargin)
//  )
//
//  nodeConfig(node3)(
//    ConfigFactory.parseString("""
//                                |akka {
//                                |  cluster {
//                                |    seed-nodes = ["akka.tcp://MutableActorInspectionSpec@127.0.0.1:2553"]
//                                |
//                                |    # auto downing is NOT safe for production deployments.
//                                |    # you may want to use it during development, read more about it in the docs.
//                                |    auto-down-unreachable-after = 10s
//                                |  }
//                                |}
//    """.stripMargin)
//  )

  commonConfig(
    ConfigFactory
      .parseString {
        """
          |akka {
          |  actor.provider = cluster
          |
          |  remote {
          |    log-received-messages = on
          |    log-remote-lifecycle-events = off
          |
          |    netty.tcp {
          |      hostname = "127.0.0.1"
          |      port = 2551
          |    }
          |
          |    artery {
          |      # change this to enabled=on to use Artery instead of netty
          |      # see https://doc.akka.io/docs/akka/current/remoting-artery.html
          |      enabled = off
          |      transport = tcp
          |      canonical.hostname = "127.0.0.1"
          |      canonical.port = 0
          |     }
          |  }
          |
          |  cluster {
          |    seed-nodes = ["akka.tcp://ActorInspectorSpec@127.0.0.1:2551"]
          |
          |    # auto downing is NOT safe for production deployments.
          |    # you may want to use it during development, read more about it in the docs.
          |    auto-down-unreachable-after = 10s
          |  }
          |}
      """.stripMargin
      }
      .withFallback(ConfigFactory.load())
  )
}
