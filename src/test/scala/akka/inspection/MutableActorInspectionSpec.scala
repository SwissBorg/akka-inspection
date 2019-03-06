package akka.inspection

import akka.actor.{ActorSystem, Props}
import akka.inspection.ActorInspection.{
  FragmentIdsRequest => _,
  FragmentIdsResponse => _,
  FragmentsRequest => _,
  FragmentsResponse => _,
  _
}
import akka.inspection.Actors.MutableActor
import akka.inspection.manager.ActorInspectorManager.InspectableActorRef
import akka.inspection.manager._
import akka.inspection.manager.state.Group
import akka.testkit.{ImplicitSender, TestKit}
import cats.data.OptionT
import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class MutableActorInspectionSpec
    extends TestKit(ActorSystem("MutableActorInspectionSpec", MutableActorInspectionSpec.testConfig))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Eventually
    with IntegrationPatience {

  "MutableActorInspectionSpec" must {
    "correctly inspect a specific fragment" in {
      val inspector = ActorInspector(system)

      val inspectableRef = InspectableActorRef(system.actorOf(Props[MutableActor]))

      val m = FragmentsRequest(List(FragmentId("yes")), inspectableRef.toId)

      val expectedFragment = Map(FragmentId("yes") -> RenderedFragment("yes = 1"))

      inspectableRef.ref ! 42

      eventually(
        Await.result(
          (for {
            grpcResponse <- OptionT.liftF(inspector.requestFragments(m.toGRPC))
            response <- OptionT.fromOption[Future](FragmentsResponse.fromGRPC(grpcResponse))
          } yield response).value.map {
            case Some(FragmentsResponse(Right(fragments))) => assert(fragments == expectedFragment)
            case r                                         => assert(false, r)
          },
          Duration.Inf
        )
      )
    }

    "correctly inspect multiple fragments" in {
      val inspector = ActorInspector(system)

      val inspectableRef = InspectableActorRef(system.actorOf(Props[MutableActor]))

      val m = FragmentsRequest(List(FragmentId("yes"), FragmentId("no")), inspectableRef.toId)

      val expectedFragment =
        Map(FragmentId("yes") -> RenderedFragment("yes = 1"), FragmentId("no") -> RenderedFragment("no = 2"))

      inspectableRef.ref ! 42

      eventually(
        Await.result(
          (for {
            grpcResponse <- OptionT.liftF(inspector.requestFragments(m.toGRPC))
            response <- OptionT.fromOption[Future](FragmentsResponse.fromGRPC(grpcResponse))
          } yield response).value.map {
            case Some(FragmentsResponse(Right(fragments))) => assert(fragments == expectedFragment)
            case r                                         => assert(false, r)
          },
          Duration.Inf
        )
      )
    }

    "correctly get the groups" in {
      val inspector = ActorInspector(system)

      val inspectableRef = InspectableActorRef(system.actorOf(Props[MutableActor]))
      val expectedGroups = List(Group("hello"), Group("world"))

      eventually(
        Await.result(
          (for {
            grpcResponse <- OptionT.liftF(inspector.requestGroups(GroupsRequest(inspectableRef.toId).toGRPC))
            response <- OptionT.fromOption[Future](GroupsResponse.fromGRPC(grpcResponse))
          } yield response).value.map {
            case Some(GroupsResponse(Right(groups))) => assert(groups == expectedGroups)
            case r                                   => assert(false, r)
          },
          Duration.Inf
        )
      )
    }

    "correctly get the fragment ids" in {
      val inspector = ActorInspector(system)

      val inspectableRef = InspectableActorRef(system.actorOf(Props[MutableActor]))
      val expectedFragmentIds = List(FragmentId("yes"), FragmentId("no"))
      val expectedState = "receive"

      eventually(
        Await.result(
          (for {
            grpcResponse <- OptionT.liftF(inspector.requestFragmentIds(FragmentIdsRequest(inspectableRef.toId).toGRPC))
            response <- OptionT.fromOption[Future](FragmentIdsResponse.fromGRPC(grpcResponse))
          } yield response).value.map {
            case Some(FragmentIdsResponse(Right((state, ids)))) =>
              assert(ids == expectedFragmentIds && state == expectedState)
            case r => assert(false, r)
          },
          Duration.Inf
        )
      )
    }
  }

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)
}

object MutableActorInspectionSpec {
  val testConfig: Config = ConfigFactory
    .parseString {
      """
        |akka {
        |  loglevel= "DEBUG"
        |
        |  actor {
        |    provider = cluster
        |  }
        |
        |  remote {
        |    log-received-messages = on
        |
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
        |    seed-nodes = ["akka.tcp://MutableActorInspectionSpec@127.0.0.1:2551"]
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
