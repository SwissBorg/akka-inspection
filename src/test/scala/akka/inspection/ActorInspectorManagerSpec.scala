package akka.inspection

import akka.actor.{Actor, ActorSystem, Props}
import akka.inspection.ActorInspection.FragmentId
import akka.inspection.ActorInspectorImpl.InspectableActorRef
import akka.inspection.ActorInspectorManager.Groups.Group
import akka.inspection.ActorInspectorManager._
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class ActorInspectorManagerSpec
    extends TestKit(ActorSystem("ActorInspectorManagerSpec"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {
  import ActorInspectorManagerSpec._

  "ActorInspectorManager" must {
    "send back all the groups of an actor" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      val groups = Set("hello", "world").map(Group)

      inspectorRef ! Put(dummyRef, Set.empty, groups)
      inspectorRef ! GroupsRequest(dummyRef.toId)
      expectMsg(GroupsResponse(Right(groups.toList)))
    }

    "handle a an empty string as a valid group name" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      val groups = Set(Group(""))

      inspectorRef ! Put(dummyRef, Set.empty, groups)
      inspectorRef ! GroupsRequest(dummyRef.toId)
      expectMsg(GroupsResponse(Right(groups.toList)))
    }

    "add groups to an actor in multiple steps" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      val helloGroups = Set(Group("hello"))
      val worldGroups = Set(Group("world"))

      inspectorRef ! Put(dummyRef, Set.empty, helloGroups)
      inspectorRef ! Put(dummyRef, Set.empty, worldGroups)
      inspectorRef ! GroupsRequest(dummyRef.toId)
      expectMsg(GroupsResponse(Right(helloGroups.toList ++ worldGroups.toList)))
    }

    "fail when requesting the groups of an undeclared actor" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      inspectorRef ! GroupsRequest(dummyRef.toId)
      expectMsg(GroupsResponse(Left(ActorNotInspectable(dummyRef.toId))))
    }

    "fail when requesting the keys of an undeclared actor" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      inspectorRef ! FragmentIdsRequest(dummyRef.toId)
      expectMsg(FragmentIdsResponse(Left(ActorNotInspectable(dummyRef.toId))))
    }

    "fail to retrieve groups if the actor was deleted" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      inspectorRef ! Put(dummyRef, Set.empty, Set(Group("hello")))
      inspectorRef ! Release(dummyRef)
      inspectorRef ! GroupsRequest(dummyRef.toId)
      expectMsg(GroupsResponse(Left(ActorNotInspectable(dummyRef.toId))))
    }

    "fail to retrieve keys if the actor was deleted" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      inspectorRef ! Put(dummyRef, Set("hello", "world").map(FragmentId), Set.empty)
      inspectorRef ! Release(dummyRef)
      inspectorRef ! FragmentIdsRequest(dummyRef.toId)
      expectMsg(FragmentIdsResponse(Left(ActorNotInspectable(dummyRef.toId))))
    }

//    "inspect an actor that has been declared" in {
//      val inspector = ActorInspector(system)
//      val dummyRef = InspectableActorRef(system.actorOf(Props[DummyInspectableActor]))
//
//      inspector.
//    }
  }

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)
}

object ActorInspectorManagerSpec {
  class NopActor extends Actor {
    override def receive: Receive = { case _ => () }
  }

//  class DummyInspectableActor extends Actor with MutableActorInspection {
//    override def receive: Receive = { case _ => () }
//    override def stateFragments: Map[FragmentId, Fragment] = Map {
//      FragmentId("yes") -> Fragment.fix("yes")
//    }
//  }

//  val testConfig: Config = ConfigFactory
//    .parseString {
//      """
//        |akka {
//        | // loglevel= "DEBUG"
//        |
//        |  actor {
//        |    provider = cluster
//        |  }
//        |
//        |  remote {
//        |    netty.tcp {
//        |      hostname = "127.0.0.1"
//        |      port = 2551
//        |    }
//        |    artery {
//        |      # change this to enabled=on to use Artery instead of netty
//        |      # see https://doc.akka.io/docs/akka/current/remoting-artery.html
//        |      enabled = off
//        |      transport = tcp
//        |      canonical.hostname = "127.0.0.1"
//        |      canonical.port = 0
//        |    }
//        |  }
//        |
//        |  cluster {
//        |    seed-nodes = ["akka.tcp://ActorInspectorManagerSpec@127.0.0.1:2551"]
//        |
//        |    # auto downing is NOT safe for production deployments.
//        |    # you may want to use it during development, read more about it in the docs.
//        |    auto-down-unreachable-after = 10s
//        |  }
//        |}
//        |
//    """.stripMargin
//    }
//    .withFallback(ConfigFactory.load())

}
