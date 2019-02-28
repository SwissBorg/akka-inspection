package test.helpers

import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig

object MultiNodeBasicConfig extends MultiNodeConfig {
  val node1: RoleName = role("node1")
  val node2: RoleName = role("node2")
  val node3: RoleName = role("node3")
}
