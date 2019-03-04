package akka.inspection

import akka.actor.{ActorSystem, Props}
import akka.inspection.ActorInspection.{
  FragmentsRequest => _,
  FragmentsResponse => _,
  FragmentIdsResponse => _,
  FragmentIdsRequest => _,
  _
}
import akka.inspection.Actors.StatelessActor
import akka.inspection.manager.ActorInspectorManager.InspectableActorRef
import akka.inspection.manager._
import akka.inspection.manager.state.Group
import akka.inspection.util.LazyFuture
import akka.testkit.{ImplicitSender, TestKit}
import cats.data.OptionT
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.Eventually
import org.scalatest.{AsyncWordSpecLike, BeforeAndAfterAll, Matchers}

class ActorInspectionSpec
    extends TestKit(ActorSystem("ActorInspectionSpec", ActorInspectionSpec.testConfig))
    with ImplicitSender
    with AsyncWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Eventually {

  "ActorInspectionSpec" must {
    "correctly inspect a specific fragment" in {
      val inspector = ActorInspector(system)

      val inspectableRef = InspectableActorRef(system.actorOf(Props[StatelessActor]))

      val m = FragmentsRequest(List(FragmentId("yes")), inspectableRef.toId)

      val expectedFragment = Map(FragmentId("yes") -> RenderedFragment("1"))

      inspectableRef.ref ! 42

      val assertion = for {
        grpcResponse <- OptionT.liftF(LazyFuture(inspector.requestFragments(m.toGRPC)))
        response <- OptionT.fromOption[LazyFuture](FragmentsResponse.fromGRPC(grpcResponse))
      } yield
        response match {
          case FragmentsResponse(Right(fragments)) => assert(fragments == expectedFragment)
          case r                                   => assert(false, r)
        }

      assertion
        .map(eventually(_))
        .fold(assert(false))(identity)
        .value
    }

    "correctly inspect multiple fragments" in {
      val inspector = ActorInspector(system)

      val inspectableRef = InspectableActorRef(system.actorOf(Props[StatelessActor]))

      val m = FragmentsRequest(List(FragmentId("yes"), FragmentId("no")), inspectableRef.toId)

      val expectedFragment = Map(FragmentId("yes") -> RenderedFragment("1"), FragmentId("no") -> RenderedFragment("2"))

      inspectableRef.ref ! 42

      val assertion = for {
        grpcResponse <- OptionT.liftF(LazyFuture(inspector.requestFragments(m.toGRPC)))
        response <- OptionT.fromOption[LazyFuture](FragmentsResponse.fromGRPC(grpcResponse))
      } yield
        response match {
          case FragmentsResponse(Right(fragments)) => assert(fragments == expectedFragment)
          case r                                   => assert(false, r)
        }

      assertion
        .map(eventually(_))
        .fold(assert(false))(identity)
        .value
    }

    "correctly get the groups" in {
      val inspector = ActorInspector(system)

      val inspectableRef = InspectableActorRef(system.actorOf(Props[StatelessActor]))
      val expectedGroups = List(Group("goodbye"), Group("universe"))

      val assertion = for {
        grpcResponse <- OptionT.liftF(LazyFuture(inspector.requestGroups(GroupsRequest(inspectableRef.toId).toGRPC)))
        response <- OptionT.fromOption[LazyFuture](GroupsResponse.fromGRPC(grpcResponse))
      } yield
        response match {
          case GroupsResponse(Right(groups)) => assert(groups == expectedGroups)
          case r                             => assert(false, r)
        }

      assertion
        .map(eventually(_))
        .fold(assert(false))(identity)
        .value
    }

    "correctly get the fragment ids" in {
      val inspector = ActorInspector(system)

      val inspectableRef = InspectableActorRef(system.actorOf(Props[StatelessActor]))
      val expectedFragmentIds = List(FragmentId("yes"), FragmentId("no"))

      val assertion = for {
        grpcResponse <- OptionT.liftF(
          LazyFuture(inspector.requestFragmentIds(FragmentIdsRequest(inspectableRef.toId).toGRPC))
        )
        response <- OptionT.fromOption[LazyFuture](FragmentIdsResponse.fromGRPC(grpcResponse))
      } yield
        response match {
          case FragmentIdsResponse(Right(groups)) => assert(groups == expectedFragmentIds)
          case r                                  => assert(false, r)
        }

      assertion
        .map(eventually(_))
        .fold(assert(false))(identity)
        .value
    }
  }

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)
}

object ActorInspectionSpec {
  val testConfig: Config = ConfigFactory
    .parseString {
      """
        |akka {
        | // loglevel= "DEBUG"
        |
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
