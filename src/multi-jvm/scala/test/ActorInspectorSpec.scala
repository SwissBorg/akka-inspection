package test

import akka.actor.Props
import akka.cluster.Cluster
import akka.inspection.ActorInspector
import akka.inspection.Actors.MutableActor
import akka.inspection.manager.{InspectableActorsRequest, InspectableActorsResponse}
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import test.helpers.{MultiNodeBasicConfig, STMultiNodeSpec}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class ActorInspectorSpecMultiJvmNode1 extends ActorInspectorSpec
class ActorInspectorSpecMultiJvmNode2 extends ActorInspectorSpec
class ActorInspectorSpecMultiJvmNode3 extends ActorInspectorSpec

class ActorInspectorSpec extends MultiNodeSpec(MultiNodeBasicConfig) with STMultiNodeSpec with ImplicitSender {
  import MultiNodeBasicConfig._

  override def initialParticipants: Int = roles.size

  "A bla" must {
//    "wait for all nodes to enter a barrier" in {
//      enterBarrier("startup")
//    }

    "do something" in {
      val node1Address = node(node1).address
      val node2Address = node(node2).address
      val node3Address = node(node3).address

      Cluster(system).join(node1Address)

      runOn(node1) {
        val inspector = ActorInspector(system)
        (0 until 2).foreach(_ => system.actorOf(Props[MutableActor]))

        enterBarrier("deployed")

        awaitAssert(inspector.requestInspectableActors(InspectableActorsRequest.toGRPC).onComplete {
          case Success(res) =>
            assert(InspectableActorsResponse.fromGRPC(res) == InspectableActorsResponse(List.empty))
          case Failure(t) =>
            assert(false, t)
            println(s"-------------- $t") //assert(false)
        })
      }

      runOn(node2, node3) {
        (0 until 2).foreach(_ => system.actorOf(Props[MutableActor]))
        enterBarrier("deployed")
      }

      enterBarrier("finished")
    }
  }
}
