package akka.inspection.manager

import akka.actor.{Actor, ActorSystem, Props}
import akka.inspection.FragmentId
import akka.inspection.manager.ActorInspectorManager._
import akka.inspection.manager.state.Group
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class ActorInspectorManagerSpec
    extends TestKit(ActorSystem("ActorInspectorManagerSpec", ActorInspectorManagerSpec.testConfig))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {
  import ActorInspectorManagerSpec._

  "ActorInspectorManager" must {
    "send back all the groups of an actor" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef     = InspectableActorRef(system.actorOf(Props[NopActor]))

      val groups = Set("hello", "world").map(Group)

      inspectorRef ! Subscribe(dummyRef, groups)
      inspectorRef ! GroupsRequest(dummyRef.toId)
      expectMsg(GroupsResponse(Right(groups.toList)))
    }

    "handle an empty string as a valid group name" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef     = InspectableActorRef(system.actorOf(Props[NopActor]))

      val groups = Set(Group(""))

      inspectorRef ! Subscribe(dummyRef, groups)
      inspectorRef ! GroupsRequest(dummyRef.toId)
      expectMsg(GroupsResponse(Right(groups.toList)))
    }

    "be able to add groups to an actor in multiple steps" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef     = InspectableActorRef(system.actorOf(Props[NopActor]))

      val helloGroups = Set(Group("hello"))
      val worldGroups = Set(Group("world"))

      inspectorRef ! Subscribe(dummyRef, helloGroups)
      inspectorRef ! Subscribe(dummyRef, worldGroups)
      inspectorRef ! GroupsRequest(dummyRef.toId)
      expectMsg(GroupsResponse(Right(helloGroups.toList ++ worldGroups.toList)))
    }

    "fail when requesting the groups of an undeclared actor" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef     = InspectableActorRef(system.actorOf(Props[NopActor]))

      inspectorRef ! GroupsRequest(dummyRef.toId)
      within(10.seconds)(expectMsg(GroupsResponse(Left(ActorNotInspectable(dummyRef.toId)))))
    }

    "fail when requesting the fragment-ids of an undeclared actor" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef     = InspectableActorRef(system.actorOf(Props[NopActor]))

      inspectorRef ! FragmentIdsRequest(dummyRef.toId)
      expectMsg(FragmentIdsResponse(Left(ActorNotInspectable(dummyRef.toId))))
    }

    "fail to retrieve groups if the actor was deleted" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef     = InspectableActorRef(system.actorOf(Props[NopActor]))

      inspectorRef ! Subscribe(dummyRef, Set(Group("hello")))
      inspectorRef ! Unsubscribe(dummyRef)
      inspectorRef ! GroupsRequest(dummyRef.toId)
      expectMsg(GroupsResponse(Left(ActorNotInspectable(dummyRef.toId))))
    }

    "fail to retrieve keys if the actor was deleted" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef     = InspectableActorRef(system.actorOf(Props[NopActor]))

      inspectorRef ! Subscribe(dummyRef, Set.empty)
      inspectorRef ! Unsubscribe(dummyRef)
      inspectorRef ! FragmentIdsRequest(dummyRef.toId)
      expectMsg(FragmentIdsResponse(Left(ActorNotInspectable(dummyRef.toId))))
    }
  }

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)
}

object ActorInspectorManagerSpec {

  class NopActor extends Actor {
    override def receive: Receive = { case _ => () }
  }

  val testConfig: Config = ConfigFactory
    .parseString {
      """
        |akka {
        |  actor {
        |    provider = cluster
        |  }
        |
        |  remote {
        |    netty.tcp {
        |      hostname = "127.0.0.1"
        |      port = 2551
        |    }
        |    artery {
        |      # change this to enabled=on to use Artery instead of netty
        |      # see https://doc.akka.io/docs/akka/current/remoting-artery.html
        |      enabled = off
        |      transport = tcp
        |      canonical.hostname = "127.0.0.1"
        |      canonical.port = 0
        |    }
        |  }
        |
        |  cluster {
        |    seed-nodes = ["akka.tcp://ActorInspectionSpec@127.0.0.1:2551"]
        |
        |    # auto downing is NOT safe for production deployments.
        |    # you may want to use it during development, read more about it in the docs.
        |    auto-down-unreachable-after = 10s
        |  }
        |}
        |
    """.stripMargin
    }
    .withFallback(ConfigFactory.load())

}
