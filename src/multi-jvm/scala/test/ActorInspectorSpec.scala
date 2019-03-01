package test

import akka.actor.Props
import akka.cluster.Cluster
import akka.inspection.ActorInspector
import akka.inspection.Actors.{MutableActor, StatelessActor}
import akka.inspection.manager.state.Group
import akka.inspection.manager.{GroupRequest, GroupResponse, InspectableActorsRequest, InspectableActorsResponse}
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Second, Seconds, Span}
import test.helpers.{MultiNodeBasicConfig, STMultiNodeSpec}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.{Failure, Success, Try}

class ActorInspectorSpecMultiJvmNode1 extends ActorInspectorSpec
class ActorInspectorSpecMultiJvmNode2 extends ActorInspectorSpec
class ActorInspectorSpecMultiJvmNode3 extends ActorInspectorSpec

class ActorInspectorSpec
    extends MultiNodeSpec(MultiNodeBasicConfig)
    with STMultiNodeSpec
    with ImplicitSender
    with Eventually {
  import MultiNodeBasicConfig._

  override def initialParticipants: Int = roles.size

  "An ActorInspectorManager" must {
    "up" in {
      Cluster(system).joinSeedNodes(List(node(node1).address))
      Cluster(system).registerOnMemberUp(enterBarrier("up"))
    }

    "get all the inspectable actors" in {
      runOn(node1) {
        val inspector = ActorInspector(system)
        (0 until 2).foreach(i => system.actorOf(Props[MutableActor], s"node1-$i"))

        enterBarrier("deployed")

        eventually {
          val p: Promise[Assertion] = Promise()
          p.completeWith(inspector.requestInspectableActors(InspectableActorsRequest.toGRPC).transform {
            case Success(res) =>
              Try(
                assert(
                  InspectableActorsResponse
                    .fromGRPC(res)
                    .inspectableActors
                    .toSet ==
                    Set(
                      "akka://ActorInspectorSpec/user/node1-0",
                      "akka://ActorInspectorSpec/user/node1-1",
                      "akka://ActorInspectorSpec/user/node2-0",
                      "akka://ActorInspectorSpec/user/node2-1",
                      "akka://ActorInspectorSpec/user/node3-1",
                      "akka://ActorInspectorSpec/user/node3-0"
                    )
                )
              )
            case Failure(t) => Try(assert(false, t))
          })

          awaitAssert(Await.result(p.future, Duration.Inf))
        }
      }

      runOn(node2) {
        (0 until 2).foreach(i => system.actorOf(Props[MutableActor], s"node2-$i"))
        enterBarrier("deployed")
      }

      runOn(node3) {
        (0 until 2).foreach(i => system.actorOf(Props[MutableActor], s"node3-$i"))
        enterBarrier("deployed")
      }

      enterBarrier("finished")
    }

    "get all the members of a group" in {
      runOn(node1) {
        val inspector = ActorInspector(system)
        (2 until 4).foreach(i => system.actorOf(Props[MutableActor], s"node1-$i"))
        (4 until 6).foreach(i => system.actorOf(Props[StatelessActor], s"node1-$i"))

        enterBarrier("deployed")

        eventually {
          val p: Promise[Assertion] = Promise()
          p.completeWith(inspector.requestGroup(GroupRequest(Group("universe")).toGRPC).transform {
            case Success(res) =>
              Try(
                assert(
                  GroupResponse
                    .fromGRPC(res)
                    .inspectableActors
                    .toSet ==
                    Set(
                      "akka://ActorInspectorSpec/user/node1-4",
                      "akka://ActorInspectorSpec/user/node1-5",
                      "akka://ActorInspectorSpec/user/node2-4",
                      "akka://ActorInspectorSpec/user/node2-5",
                      "akka://ActorInspectorSpec/user/node3-4",
                      "akka://ActorInspectorSpec/user/node3-5"
                    )
                )
              )
            case Failure(t) => Try(assert(false, t))
          })

          awaitAssert(Await.result(p.future, Duration.Inf))
        }
      }

      runOn(node2) {
        (2 until 4).foreach(i => system.actorOf(Props[MutableActor], s"node2-$i"))
        (4 until 6).foreach(i => system.actorOf(Props[StatelessActor], s"node2-$i"))
        enterBarrier("deployed")
      }

      runOn(node3) {
        (2 until 4).foreach(i => system.actorOf(Props[MutableActor], s"node3-$i"))
        (4 until 6).foreach(i => system.actorOf(Props[StatelessActor], s"node3-$i"))
        enterBarrier("deployed")
      }

      enterBarrier("finished")
    }

  }

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(30, Seconds)), interval = scaled(Span(1, Second)))

}
