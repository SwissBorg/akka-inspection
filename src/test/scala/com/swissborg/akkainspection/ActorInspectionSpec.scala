package com.swissborg.akkainspection

import akka.actor.{ActorSystem, Props}
import com.swissborg.akkainspection.ActorInspection.{FragmentIdsRequest => _, FragmentIdsResponse => _, FragmentsRequest => _, FragmentsResponse => _, _}
import com.swissborg.akkainspection.Actors.StatelessActor
import com.swissborg.akkainspection.extension.ActorInspector
import com.swissborg.akkainspection.inspectable.Inspectable
import com.swissborg.akkainspection.inspectable.derivation.semiauto._
import com.swissborg.akkainspection.manager.ActorInspectorManager.InspectableActorRef
import com.swissborg.akkainspection.manager._
import akka.testkit.{ImplicitSender, TestKit}
import cats.data.OptionT
import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class ActorInspectionSpec
    extends TestKit(ActorSystem("ActorInspectionSpec", ActorInspectionSpec.testConfig))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Eventually
    with IntegrationPatience {

  "ActorInspectionSpec" must {
    "correctly expand a fragment-id with a wildcard in first position" in {
      final case class Baz(inga: Set[List[Char]])
      final case class Bar(l: List[Double], baz: Baz)
      final case class Foo(i: Int, s: String, bar: Bar)

      implicit val inspectableFoo: Inspectable[Foo] = deriveInspectable

      assert(
        FragmentId("*").expand(inspectableFoo.fragments.keySet) == Set("i", "s", "bar.l", "bar.baz.inga")
            .map(FragmentId)
      )
    }

    "correctly expand a fragment-id with a wildcard in a sub-position" in {
      final case class Baz(inga: Set[List[Char]])
      final case class Bar(l: List[Double], baz: Baz)
      final case class Foo(i: Int, s: String, bar: Bar)

      implicit val inspectableFoo: Inspectable[Foo] = deriveInspectable

      assert(
        FragmentId("bar.*").expand(inspectableFoo.fragments.keySet) == Set("bar.l", "bar.baz.inga").map(FragmentId)
      )
    }

    "correctly expand a fragment-id without a wildcard" in {
      final case class Baz(inga: Set[List[Char]])
      final case class Bar(l: List[Double], baz: Baz)
      final case class Foo(i: Int, s: String, bar: Bar)

      implicit val inspectableFoo: Inspectable[Foo] = deriveInspectable

      assert(
        FragmentId("bar.baz.inga")
          .expand(inspectableFoo.fragments.keySet) == Set(FragmentId("bar.baz.inga"))
      )
    }

    "correctly expand a fragment-id that do not exist" in {
      final case class Baz(inga: Set[List[Char]])
      final case class Bar(l: List[Double], baz: Baz)
      final case class Foo(i: Int, s: String, bar: Bar)

      implicit val inspectableFoo: Inspectable[Foo] = deriveInspectable

      assert(
        FragmentId("bar").expand(inspectableFoo.fragments.keySet) == Set
            .empty[FragmentId]
      )
    }

    "correctly inspect a specific fragment" in {
      val inspector = ActorInspector(system)

      val inspectableRef =
        InspectableActorRef(system.actorOf(Props[StatelessActor]))

      val m = FragmentsRequest(List(FragmentId("yes")), inspectableRef.toId)

      val expectedFragment = Map(FragmentId("yes") -> RenderedFragment("1"))

      inspectableRef.ref ! 42

      eventually(
        Await.result(
          (for {
            grpcResponse <- OptionT.liftF(inspector.requestFragments(m.toGRPC))
            response     <- OptionT.fromOption[Future](FragmentsResponse.fromGRPC(grpcResponse))
          } yield response).value.map {
            case Some(FragmentsResponse(Right((_, fragments)))) =>
              assert(fragments == expectedFragment)
            case r => assert(false, r)
          },
          Duration.Inf
        )
      )
    }

    "correctly inspect multiple fragments" in {
      val inspector = ActorInspector(system)

      val inspectableRef =
        InspectableActorRef(system.actorOf(Props[StatelessActor]))

      val m = FragmentsRequest(List(FragmentId("yes"), FragmentId("no")), inspectableRef.toId)

      val expectedFragment =
        Map(FragmentId("yes") -> RenderedFragment("1"), FragmentId("no") -> RenderedFragment("2"))

      inspectableRef.ref ! 42

      eventually(
        Await.result(
          (for {
            grpcResponse <- OptionT.liftF(inspector.requestFragments(m.toGRPC))
            response     <- OptionT.fromOption[Future](FragmentsResponse.fromGRPC(grpcResponse))
          } yield response).value.map {
            case Some(FragmentsResponse(Right((_, fragments)))) =>
              assert(fragments == expectedFragment)
            case r => assert(false, r)
          },
          Duration.Inf
        )
      )
    }

    "correctly inspect all the fragments using a wildcard" in {
      val inspector = ActorInspector(system)

      val inspectableRef =
        InspectableActorRef(system.actorOf(Props[StatelessActor]))

      val m = FragmentsRequest(List(FragmentId("*")), inspectableRef.toId)

      val expectedFragment =
        Map(
          FragmentId("yes")            -> RenderedFragment("1"),
          FragmentId("no")             -> RenderedFragment("2"),
          FragmentId("maybe.maybeYes") -> RenderedFragment("3"),
          FragmentId("maybe.maybeNo")  -> RenderedFragment("4")
        )

      inspectableRef.ref ! 42

      eventually(
        Await.result(
          (for {
            grpcResponse <- OptionT.liftF(inspector.requestFragments(m.toGRPC))
            response     <- OptionT.fromOption[Future](FragmentsResponse.fromGRPC(grpcResponse))
          } yield response).value.map {
            case Some(FragmentsResponse(Right((_, fragments)))) =>
              assert(fragments == expectedFragment)
            case r => assert(false, r)
          },
          Duration.Inf
        )
      )
    }

    "correctly inspect sub-fragments using a wildcard" in {
      val inspector = ActorInspector(system)

      val inspectableRef =
        InspectableActorRef(system.actorOf(Props[StatelessActor]))

      val m = FragmentsRequest(List(FragmentId("maybe.*")), inspectableRef.toId)

      val expectedFragment =
        Map(
          FragmentId("maybe.maybeYes") -> RenderedFragment("3"),
          FragmentId("maybe.maybeNo")  -> RenderedFragment("4")
        )

      inspectableRef.ref ! 42

      eventually(
        Await.result(
          (for {
            grpcResponse <- OptionT.liftF(inspector.requestFragments(m.toGRPC))
            response     <- OptionT.fromOption[Future](FragmentsResponse.fromGRPC(grpcResponse))
          } yield response).value.map {
            case Some(FragmentsResponse(Right((_, fragments)))) =>
              assert(fragments == expectedFragment)
            case r => assert(false, r)
          },
          Duration.Inf
        )
      )
    }

    "correctly get the fragment ids" in {
      val inspector = ActorInspector(system)

      val inspectableRef =
        InspectableActorRef(system.actorOf(Props[StatelessActor]))
      val expectedFragmentIds =
        Set(FragmentId("yes"), FragmentId("no"), FragmentId("maybe.maybeNo"), FragmentId("maybe.maybeYes"))
      val expectedState = "main"

      eventually(
        Await.result(
          (for {
            grpcResponse <- OptionT.liftF(inspector.requestFragmentIds(FragmentIdsRequest(inspectableRef.toId).toGRPC))
            response     <- OptionT.fromOption[Future](FragmentIdsResponse.fromGRPC(grpcResponse))
          } yield response).value.map {
            case Some(FragmentIdsResponse(Right((state, ids)))) =>
              assert(ids.toSet == expectedFragmentIds && state == expectedState)
            case r => assert(false, r)
          },
          Duration.Inf
        )
      )
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
