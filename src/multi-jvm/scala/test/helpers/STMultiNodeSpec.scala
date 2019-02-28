package test.helpers

import akka.remote.testkit.MultiNodeSpecCallbacks
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

trait STMultiNodeSpec extends MultiNodeSpecCallbacks with WordSpecLike with Matchers with BeforeAndAfterAll {
  override def beforeAll(): Unit = multiNodeSpecBeforeAll()
  override def afterAll(): Unit = multiNodeSpecAfterAll()

//  implicit override def convertToWordSpecStringWrapper(s: String): WordSpecStringWrapper =
//    new WordSpecStringWrapper(s"$s (on node '${self.myself.name}', $getClass)")
}
