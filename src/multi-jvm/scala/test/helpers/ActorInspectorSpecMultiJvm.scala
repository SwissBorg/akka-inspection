package test.helpers

import akka.actor.Props
import akka.inspection.ActorInspector
import akka.inspection.Actors.MutableActor
import akka.inspection.manager.{InspectableActorsRequest, InspectableActorsResponse}
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class ActorInspectorSpecMultiJvmNode1 extends ActorInspectorSpecMultiJvm
class ActorInspectorSpecMultiJvmNode2 extends ActorInspectorSpecMultiJvm
class ActorInspectorSpecMultiJvmNode3 extends ActorInspectorSpecMultiJvm

class ActorInspectorSpecMultiJvm extends MultiNodeSpec(MultiNodeBasicConfig) with STMultiNodeSpec with ImplicitSender {
  import MultiNodeBasicConfig._

  override def initialParticipants: Int = roles.size

  "A bla" must {
    "wait for all nodes to enter a barrier" in {
      enterBarrier("startup")
    }

    "do something" in {
      runOn(node1) {
        val inspector = ActorInspector(system)

        enterBarrier("deployed")
        (0 until 2).foreach(_ => system.actorOf(Props[MutableActor]))

        inspector.requestInspectableActors(InspectableActorsRequest.toGRPC).onComplete {
          case Success(res) => assert(InspectableActorsResponse.fromGRPC(res) == InspectableActorsResponse(List.empty))
          case Failure(_)   => assert(false)
        }
      }

      runOn(node2) {
        enterBarrier("deployed")
        (0 until 2).foreach(_ => system.actorOf(Props[MutableActor]))
      }

      runOn(node3) {
        enterBarrier("deployed")
        (0 until 2).foreach(_ => system.actorOf(Props[MutableActor]))
      }

      enterBarrier("finished")
    }
  }
}
