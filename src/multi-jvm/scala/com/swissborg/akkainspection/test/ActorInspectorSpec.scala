package com.swissborg.akkainspection.test

import akka.actor.Props
import akka.cluster.Cluster
import com.swissborg.akkainspection.Actors.{MutableActor, StatelessActor}
import com.swissborg.akkainspection.extension.ActorInspector
import com.swissborg.akkainspection.manager._
import com.swissborg.akkainspection.manager.state.Group
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import org.scalatest.Assertion
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import com.swissborg.akkainspection.test.helpers.{MultiNodeBasicConfig, STMultiNodeSpec}

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
    with Eventually
    with IntegrationPatience {

  import MultiNodeBasicConfig._

  override def initialParticipants: Int = roles.size

  "An ActorInspectorManager" must {
    "up" in {
      Cluster(system).joinSeedNodes(List(node(node1).address))
      Cluster(system).registerOnMemberUp(enterBarrier("up"))
      enterBarrier("done")
    }

    "get all the inspectable actors" in {
      runOn(node1) {
        (0 until 2).foreach(i => system.actorOf(Props[MutableActor], s"node1-mutable-$i"))
      }

      runOn(node2) {
        (0 until 2).foreach(i => system.actorOf(Props[MutableActor], s"node2-mutable-$i"))
      }

      runOn(node3) {
        (0 until 2).foreach(i => system.actorOf(Props[MutableActor], s"node3-mutable-$i"))
      }

      enterBarrier("deployed")

      runOn(node1) {
        val inspector = ActorInspector(system)

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
                      "akka://ActorInspectorSpec/user/node1-mutable-0",
                      "akka://ActorInspectorSpec/user/node1-mutable-1",
                      "akka://ActorInspectorSpec/user/node2-mutable-0",
                      "akka://ActorInspectorSpec/user/node2-mutable-1",
                      "akka://ActorInspectorSpec/user/node3-mutable-1",
                      "akka://ActorInspectorSpec/user/node3-mutable-0"
                    )
                )
              )
            case Failure(t) => Try(assert(false, t))
          })

          awaitAssert(Await.result(p.future, Duration.Inf))
        }
      }

      enterBarrier("finished")
    }

    "get all the members of a group" in {
      runOn(node1) {
        val inspector = ActorInspector(system)
        (2 until 4).foreach(i => system.actorOf(Props[MutableActor], s"node1-mutable-$i"))
        (0 until 2).foreach(i => system.actorOf(Props[StatelessActor], s"node1-immutable-$i"))

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
                      "akka://ActorInspectorSpec/user/node1-immutable-0",
                      "akka://ActorInspectorSpec/user/node1-immutable-1",
                      "akka://ActorInspectorSpec/user/node2-immutable-0",
                      "akka://ActorInspectorSpec/user/node2-immutable-1",
                      "akka://ActorInspectorSpec/user/node3-immutable-0",
                      "akka://ActorInspectorSpec/user/node3-immutable-1"
                    )
                )
              )
            case Failure(t) => Try(assert(false, t))
          })

          awaitAssert(Await.result(p.future, Duration.Inf))
        }
      }

      runOn(node2) {
        (2 until 4).foreach(i => system.actorOf(Props[MutableActor], s"node2-mutable-$i"))
        (0 until 2).foreach(i => system.actorOf(Props[StatelessActor], s"node2-immutable-$i"))
        enterBarrier("deployed")
      }

      runOn(node3) {
        (2 until 4).foreach(i => system.actorOf(Props[MutableActor], s"node3-mutable-$i"))
        (0 until 2).foreach(i => system.actorOf(Props[StatelessActor], s"node3-immutable-$i"))
        enterBarrier("deployed")
      }

      enterBarrier("finished")
    }

    "get the fragment ids from an actor on the same node" in {
      runOn(node1) {
        (2 until 4).foreach(i => system.actorOf(Props[StatelessActor], s"node1-immutable-$i"))
      }

      runOn(node2) {
        (4 until 6).foreach(i => system.actorOf(Props[MutableActor], s"node2-mutable-$i"))
      }

      runOn(node3) {
        (4 until 6).foreach(i => system.actorOf(Props[MutableActor], s"node3-mutable-$i"))
      }

      enterBarrier("deployed")

      runOn(node1) {
        val inspector = ActorInspector(system)

        eventually {
          val p: Promise[Assertion] = Promise()
          p.completeWith(
            inspector
              .requestFragmentIds(FragmentIdsRequest("akka://ActorInspectorSpec/user/node1-immutable-2").toGRPC)
              .transform {
                case Success(res) =>
                  Try(
                    assert(
                      FragmentIdsResponse
                        .fromGRPC(res)
                        .exists(
                          _.ids
                            .fold(
                              _ => false,
                              r =>
                                r._2
                                  .map(_.id)
                                  .toSet == Set("yes", "no", "maybe.maybeYes", "maybe.maybeNo") && r._1 == "main"
                            )
                        )
                    )
                  )
                case Failure(t) => Try(assert(false, t))
              }
          )

          awaitAssert(Await.result(p.future, Duration.Inf))
        }
      }

      enterBarrier("done")
    }

    "get the fragment ids from an actor on another node" in {
      runOn(node1) {
        (4 until 6).foreach(i => system.actorOf(Props[StatelessActor], s"node1-mutable-$i"))
      }

      runOn(node2) {
        (2 until 4).foreach(i => system.actorOf(Props[StatelessActor], s"node2-immutable-$i"))
        (6 until 8).foreach(i => system.actorOf(Props[MutableActor], s"node2-mutable-$i"))
      }

      runOn(node3) {
        (6 until 8).foreach(i => system.actorOf(Props[MutableActor], s"node3-mutable-$i"))
      }

      enterBarrier("deployed")

      runOn(node1) {
        val inspector = ActorInspector(system)

        eventually {
          val p: Promise[Assertion] = Promise()
          p.completeWith(
            inspector
              .requestFragmentIds(FragmentIdsRequest("akka://ActorInspectorSpec/user/node2-immutable-2").toGRPC)
              .transform {
                case Success(res) =>
                  Try(
                    assert(
                      FragmentIdsResponse
                        .fromGRPC(res)
                        .exists(
                          _.ids
                            .fold(
                              _ => false,
                              r =>
                                r._2
                                  .map(_.id)
                                  .toSet == Set("yes", "no", "maybe.maybeYes", "maybe.maybeNo") && r._1 == "main"
                            )
                        )
                    )
                  )
                case Failure(t) => Try(assert(false, t))
              }
          )

          awaitAssert(Await.result(p.future, Duration.Inf))
        }

      }

      enterBarrier("done")
    }

  }
}
