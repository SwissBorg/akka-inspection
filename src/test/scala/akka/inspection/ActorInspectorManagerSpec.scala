package akka.inspection

import akka.actor.{Actor, ActorSystem, Props}
import akka.inspection
import akka.inspection.ActorInspection.{QueryRequest, QueryResponse}
import akka.inspection.ActorInspectorManager.Groups.Group
import akka.inspection.ActorInspectorManager.Keys.Key
import akka.inspection.ActorInspectorManager._
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import akka.inspection.util.ActorRefUtil._
import cats.Show

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
      val dummyRef     = system.actorOf(Props[NopActor])

      val groups = Set("hello", "world").map(Group)

      inspectorRef ! Put(dummyRef, Set.empty, groups)
      inspectorRef ! ActorGroupsRequest(asString(dummyRef))
      expectMsg(ActorGroupsResponse(Right(groups)))
    }

    "handle a an empty string as a valid group name" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef     = system.actorOf(Props[NopActor])

      val groups = Set(Group(""))

      inspectorRef ! Put(dummyRef, Set.empty, groups)
      inspectorRef ! ActorGroupsRequest(asString(dummyRef))
      expectMsg(ActorGroupsResponse(Right(groups)))
    }

    "add groups to an actor in multiple steps" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef     = system.actorOf(Props[NopActor])

      val helloGroups = Set(Group("hello"))
      val worldGroups = Set(Group("world"))

      inspectorRef ! Put(dummyRef, Set.empty, helloGroups)
      inspectorRef ! Put(dummyRef, Set.empty, worldGroups)
      inspectorRef ! ActorGroupsRequest(asString(dummyRef))
      expectMsg(ActorGroupsResponse(Right(helloGroups ++ worldGroups)))
    }

    "fail when requesting the groups of an undeclared actor" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef     = system.actorOf(Props[NopActor])

      inspectorRef ! ActorGroupsRequest(asString(dummyRef))
      expectMsg(ActorGroupsResponse(Left(ActorNotInspectable)))
    }

    "fail when requesting the keys of an undeclared actor" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef     = system.actorOf(Props[NopActor])

      inspectorRef ! ActorKeysRequest(asString(dummyRef))
      expectMsg(ActorKeysResponse(Left(ActorNotInspectable)))
    }

    "fail to retrieve groups if the actor was deleted" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef     = system.actorOf(Props[NopActor])

      inspectorRef ! Put(dummyRef, Set.empty, Set(Group("hello")))
      inspectorRef ! Release(dummyRef)
      inspectorRef ! ActorGroupsRequest(asString(dummyRef))
      expectMsg(ActorGroupsResponse(Left(ActorNotInspectable)))
    }

    "fail to retrieve keys if the actor was deleted" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef     = system.actorOf(Props[NopActor])

      inspectorRef ! Put(dummyRef, Set("hello", "world").map(Key), Set.empty)
      inspectorRef ! Release(dummyRef)
      inspectorRef ! ActorKeysRequest(asString(dummyRef))
      expectMsg(ActorKeysResponse(Left(ActorNotInspectable)))
    }

//    "bla" in {
//      val testRef = system.actorOf(Props[TestActor])
//
//      testRef ! QueryRequest.One(Key("yes"))
//      Thread.sleep(1000)
//      testRef ! QueryRequest.One(Key("yes"))
////      expectMsg(QueryResponse.Success("0"))
////      testRef ! QueryRequest.One(Key("yes"))
////      expectMsg(QueryResponse.Success("1"))
//    }

    "foo" in {
      case class Bla()

      val testRef = system.actorOf(Props[StatelessActor])
      testRef ! QueryRequest.One(Key("yes"))
      expectMsg(QueryResponse.Success("0"))
      testRef ! Bla()
      testRef ! QueryRequest.One(Key("yes"))
      expectMsg(QueryResponse.Success("1"))
    }
  }

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)
}

object ActorInspectorManagerSpec {
  implicit val intShow: Show[Int] = (t: Int) => t.toString

  class NopActor extends Actor {
    override def receive: Receive = { case a => println(a) }
  }

  class TestActor extends Actor with ActorInspection[Unit] {
    var i: Int = 0

    override def receive: Receive = inspectableReceive(()) {
      case _ => i += 1
    }

    override def responses(s: Unit): Map[Key, QueryResponse] = Map {
      Key("yes") -> QueryResponse.later(i)
    }
  }

  class StatelessActor extends Actor with ActorInspection[StatelessActor.State] {
    override def receive: Receive = mainReceive(StatelessActor.State(0))

    def mainReceive(s: StatelessActor.State): Receive = inspectableReceive(s) {
      case _ =>
        context.become(mainReceive(s.copy(i = s.i + 1)))
    }

    override def responses(s: StatelessActor.State): Map[Key, QueryResponse] = Map {
      Key("yes") -> QueryResponse.now(s.i)
    }
  }

  object StatelessActor {
    case class State(i: Int)
  }
}
