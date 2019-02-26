package akka.inspection

import akka.actor.{Actor, ActorSystem, Props}
import akka.inspection.ActorInspection._
import akka.inspection.ActorInspectorImpl.InspectableActorRef
import akka.inspection.util.Render
import akka.testkit.{ImplicitSender, TestKit}
import cats.data.OptionT
import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{AsyncWordSpecLike, BeforeAndAfterAll, Matchers}

import scala.concurrent.Future

class MutableActorInspectionSpec
    extends TestKit(ActorSystem("MutableActorInspectionSpec", MutableActorInspectionSpec.testConfig))
    with ImplicitSender
    with AsyncWordSpecLike
    with Matchers
    with BeforeAndAfterAll {
  import MutableActorInspectionSpec._

  "MutableActorInspectionSpec" must {
    "correctly render the fragments" in {
      val inspector = ActorInspector(system)
      val inspectableRef = InspectableActorRef(system.actorOf(Props[MutableActor]))

      val m = ActorInspectorManager.FragmentsRequest(List(FragmentId("yes")), inspectableRef.toId).toGRPC
      val expectedFragment0 = Map(FragmentId("yes") -> RenderedFragment("0"))
      val expectedFragment1 = Map(FragmentId("yes") -> RenderedFragment("1"))

      val res = for {
        r <- OptionT.liftF(inspector.requestFragments(m))
        res1 <- OptionT.fromOption[Future](ActorInspectorManager.FragmentsResponse.fromGRPC(r).map {
          case ActorInspectorManager.FragmentsResponse(Right(fragments)) => fragments == expectedFragment0
          case _                                                         => false
        })

        _ = inspectableRef.ref ! 42

        r <- OptionT.liftF(inspector.requestFragments(m))
        res2 <- OptionT.fromOption[Future](ActorInspectorManager.FragmentsResponse.fromGRPC(r).map {
          case ActorInspectorManager.FragmentsResponse(Right(fragments)) => fragments == expectedFragment1
          case _                                                         => false
        })
      } yield assert(res1 && res2)

      res.fold(assert(false))(identity)
    }
  }

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)
}

object MutableActorInspectionSpec {
  implicit val intShow: Render[Int] = (t: Int) => t.toString

  class MutableActor extends Actor with MutableActorInspection {
    var i: Int = 0

    override def receive: Receive = withInspection {
      case _ => i += 1
    }

    override val stateFragments: Map[FragmentId, Fragment] = Map {
      FragmentId("yes") -> Fragment.always(i)
    }
  }

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
        |    seed-nodes = ["akka.tcp://ActorInspectorManagerSpec@127.0.0.1:2551"]
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
