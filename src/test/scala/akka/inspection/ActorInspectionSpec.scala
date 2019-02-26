package akka.inspection

import akka.actor.{Actor, ActorSystem, Props}
import akka.inspection.ActorInspection._
import akka.inspection.ActorInspectorImpl.InspectableActorRef
import akka.inspection.MutableActorInspectionSpec.MutableActor
import akka.inspection.util.{LazyFuture, Render}
import akka.testkit.{ImplicitSender, TestKit}
import cats.data.OptionT
import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.Eventually
import org.scalatest.{AsyncWordSpecLike, BeforeAndAfterAll, Matchers}

import scala.concurrent.Future

class ActorInspectionSpec
    extends TestKit(ActorSystem("ActorInspectionSpec", ActorInspectionSpec.testConfig))
    with ImplicitSender
    with AsyncWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Eventually {
  import ActorInspectionSpec._

  "ActorInspectionSpec" must {
    "Observe state change" in {
      val inspector = ActorInspector(system)

      val inspectableRef = InspectableActorRef(system.actorOf(Props[StatelessActor]))

      val m = ActorInspectorManager.FragmentsRequest(List(FragmentId("yes")), inspectableRef.toId).toGRPC
      val expectedFragment1 = Map(FragmentId("yes") -> RenderedFragment("1"))

      inspectableRef.ref ! 42

      val f = for {
        response <- OptionT.liftF(LazyFuture(() => inspector.requestFragments(m)))
        response <- OptionT.fromOption[LazyFuture](ActorInspectorManager.FragmentsResponse.fromGRPC(response))
        assertion = response match {
          case ActorInspectorManager.FragmentsResponse(Right(fragments)) => assert(fragments == expectedFragment1)
          case r                                                         => assert(false, r)
        }
      } yield assertion

      f.map(eventually(_)).fold(assert(false))(identity).run

    }
  }

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)
}

object ActorInspectionSpec {
  implicit val intShow: Render[Int] = (t: Int) => t.toString

  class StatelessActor extends Actor with ActorInspection[StatelessActor.State] {
    override def receive: Receive = mainReceive(StatelessActor.State(0))

    def mainReceive(s: StatelessActor.State): Receive = withInspection(s) {
      case _ => context.become(mainReceive(s.copy(i = s.i + 1)))
    }

    override def stateFragments: Map[FragmentId, Fragment] = Map {
      FragmentId("yes") -> Fragment.state(s => s.i)
    }
  }

  object StatelessActor {
    case class State(i: Int)
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
