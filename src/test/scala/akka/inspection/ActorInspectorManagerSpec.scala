package akka.inspection

import akka.actor.{Actor, ActorSystem, Props}
import akka.inspection.ActorInspection.{StateFragment, StateFragmentId, StateFragmentRequest, StateFragmentResponse}
import akka.inspection.ActorInspectorImpl.InspectableActorRef
import akka.inspection.ActorInspectorManager.Groups.Group
import akka.inspection.ActorInspectorManager._
import akka.testkit.{ImplicitSender, TestKit}
import cats.Show
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.util.Success

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
      inspectorRef ! ActorGroupsRequest(dummyRef.toId)
      expectMsg(ActorGroupsResponse(Right(groups)))
    }

    "handle a an empty string as a valid group name" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      val groups = Set(Group(""))

      inspectorRef ! Put(dummyRef, Set.empty, groups)
      inspectorRef ! ActorGroupsRequest(dummyRef.toId)
      expectMsg(ActorGroupsResponse(Right(groups)))
    }

    "add groups to an actor in multiple steps" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      val helloGroups = Set(Group("hello"))
      val worldGroups = Set(Group("world"))

      inspectorRef ! Put(dummyRef, Set.empty, helloGroups)
      inspectorRef ! Put(dummyRef, Set.empty, worldGroups)
      inspectorRef ! ActorGroupsRequest(dummyRef.toId)
      expectMsg(ActorGroupsResponse(Right(helloGroups ++ worldGroups)))
    }

    "fail when requesting the groups of an undeclared actor" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      inspectorRef ! ActorGroupsRequest(dummyRef.toId)
      expectMsg(ActorGroupsResponse(Left(ActorNotInspectable)))
    }

    "fail when requesting the keys of an undeclared actor" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      inspectorRef ! StateFragmentIdsRequest(dummyRef.toId)
      expectMsg(StateFragmentIdsResponse(Left(ActorNotInspectable)))
    }

    "fail to retrieve groups if the actor was deleted" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      inspectorRef ! Put(dummyRef, Set.empty, Set(Group("hello")))
      inspectorRef ! Release(dummyRef)
      inspectorRef ! ActorGroupsRequest(dummyRef.toId)
      expectMsg(ActorGroupsResponse(Left(ActorNotInspectable)))
    }

    "fail to retrieve keys if the actor was deleted" in {
      val inspectorRef = system.actorOf(Props[ActorInspectorManager])
      val dummyRef = InspectableActorRef(system.actorOf(Props[NopActor]))

      inspectorRef ! Put(dummyRef, Set("hello", "world").map(StateFragmentId), Set.empty)
      inspectorRef ! Release(dummyRef)
      inspectorRef ! StateFragmentIdsRequest(dummyRef.toId)
      expectMsg(StateFragmentIdsResponse(Left(ActorNotInspectable)))
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
      implicit val ec: ExecutionContext = system.getDispatcher

      val inspector = ActorInspector(system)

      val inspectableRef = InspectableActorRef(system.actorOf(Props[StatelessActor]))

      val requests = List(StateFragmentId("yes"))

      // eeh
      inspector.f(StateFragmentRequest(requests, inspectableRef.ref)).onComplete {
        case Success(Right(StateFragmentResponse(fragments, _))) =>
          assertResult(requests.map((_, StateFragment.apply(0))))(fragments)
        case _ => assert(false)
      }

//      case class Bla()
//
//      val testRef = system.actorOf(Props[StatelessActor])
//      val requests = List(StateFragmentId("yes"))
//
//      testRef ! StateFragmentRequest(requests, testRef)
//      expectMsg(StateFragmentResponse(requests.map((_, StateFragment.now(0))), testRef))
//
//      testRef ! Bla()
//
//      testRef ! StateFragmentRequest(requests, testRef)
//      expectMsg(StateFragmentResponse(requests.map((_, StateFragment.now(1))), testRef))
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

    override def receive: Receive = inspectableReceive {
      case _ => i += 1
    }

    /**
     * Description of how to generate [[StateFragment]]s given the state `s`.
     *
     * @param s the actor's state.
     * @return mapping from
     */
    override def stateFragments: Map[StateFragmentId, StateFragment] = Map {
      StateFragmentId("yes") -> StateFragment(5)
    }
  }

  class StatelessActor extends Actor with ActorInspection[StatelessActor.State] {
    override def receive: Receive = mainReceive(StatelessActor.State(0))

    def mainReceive(s: StatelessActor.State): Receive = withInspection(s) {
      case _ => context.become(mainReceive(s.copy(i = s.i + 1)))
    }

    implicit override def showS: Show[StatelessActor.State] = (t: StatelessActor.State) => t.toString

    /**
     * Description of how to generate [[StateFragment]]s given the state `s`.
     *
     * @param s the actor's state.
     * @return mapping from
     */
    override def stateFragments: Map[StateFragmentId, StateFragment] = Map {
      StateFragmentId("yes") -> StateFragment.state(s => s.i)
    }
  }

  object StatelessActor {
    case class State(i: Int)
  }
}
