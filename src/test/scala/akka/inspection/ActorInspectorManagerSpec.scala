package akka.inspection

import akka.actor.{Actor, ActorSystem, Props}
import akka.inspection.ActorInspection._
import akka.inspection.ActorInspectorImpl.InspectableActorRef
import akka.inspection.ActorInspectorManager.Groups.Group
import akka.inspection.ActorInspectorManager._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import cats.Show
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
      expectMsg(GroupsResponse(Right(groups)))
    }

    "handle a an empty string as a valid group name" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      val groups = Set(Group(""))

      inspectorRef ! Put(dummyRef, Set.empty, groups)
      inspectorRef ! GroupsRequest(dummyRef.toId)
      expectMsg(GroupsResponse(Right(groups)))
    }

    "add groups to an actor in multiple steps" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      val helloGroups = Set(Group("hello"))
      val worldGroups = Set(Group("world"))

      inspectorRef ! Put(dummyRef, Set.empty, helloGroups)
      inspectorRef ! Put(dummyRef, Set.empty, worldGroups)
      inspectorRef ! GroupsRequest(dummyRef.toId)
      expectMsg(GroupsResponse(Right(helloGroups ++ worldGroups)))
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

    "bla" in {
      val testRef = system.actorOf(Props[TestActor])
      val initiatorProbe = TestProbe()
      val testProbe = TestProbe()
      testRef ! FragmentsRequest(Set(FragmentId("yes")), testProbe.ref, initiatorProbe.ref)

      testProbe.expectMsg(
        FragmentsResponse(Map(FragmentId("yes") -> RenderedFragment("0")), initiatorProbe.ref)
      )

      initiatorProbe.expectNoMessage()
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

  class TestActor extends Actor with MutableActorInspection {
    var i: Int = 0

    override def receive: Receive = withInspection {
      case _ => i += 1
    }

    /**
     * Description of how to generate [[StateFragment]]s given the state `s`.
     *
     * @param s the actor's state.
     * @return mapping from
     */
    override def stateFragments: Map[StateFragmentId, StateFragment] = Map {
      FragmentId("yes") -> StateFragment(5)
    }
  }

  class StatelessActor extends Actor with ActorInspection[StatelessActor.State] {
    override def receive: Receive = mainReceive(StatelessActor.State(0))

    def mainReceive(s: StatelessActor.State): Receive = withInspection(s) {
      case _ =>
        println("HEERREE")
        context.become(mainReceive(s.copy(i = s.i + 1)))
    }

    override def stateFragments: Map[StateFragmentId, StateFragment] = Map {
      FragmentId("yes") -> StateFragment.state(_.i)
    }
  }

  object StatelessActor {
    case class State(i: Int)
  }
}
