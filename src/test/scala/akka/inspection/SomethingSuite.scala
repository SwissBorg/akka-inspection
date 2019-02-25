package akka.inspection

import akka.actor.{Actor, ActorSystem, Props}
import akka.inspection.ActorInspection._
import akka.inspection.ActorInspectorImpl.InspectableActorRef
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import cats.Show
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{AsyncWordSpecLike, BeforeAndAfterAll, Matchers}

class SomethingSuite
    extends TestKit(ActorSystem("ActorInspectorManagerSpec", SomethingSuite.testConfig))
    with ImplicitSender
    with AsyncWordSpecLike
    with Matchers
    with BeforeAndAfterAll {
  import SomethingSuite._

  "foo" in {
    val inspector = ActorInspector(system)

    val inspectableRef = InspectableActorRef(system.actorOf(Props[StatelessActor]))
    val initiatorProbe = TestProbe()
    val replyToProbe = TestProbe()

    val requests = Set(StateFragmentId("yes"))

    val m = grpc.StateFragmentsRequest("0", inspectableRef.toId, List("yes"))
    val expectedFragment0 = Map("yes" -> "0")
    val expectedFragment1 = Map("yes" -> "1")

    for {
      res1 <- inspector
        .requestStateFragments(m)
        .map {
          case grpc.StateFragmentsResponse(
              grpc.StateFragmentsResponse.FragmentsRes.Success(grpc.StateFragmentsResponse.Fragments(fragments))
              ) =>
            println(fragments)
            fragments == expectedFragment0
          case _ => false
        }

      _ = inspectableRef.ref ! 42

      res2 <- inspector
        .requestStateFragments(m)
        .map {
          case grpc.StateFragmentsResponse(
              grpc.StateFragmentsResponse.FragmentsRes.Success(grpc.StateFragmentsResponse.Fragments(fragments))
              ) =>
            println(fragments)
            fragments == expectedFragment1
          case _ => false
        }
    } yield assert(res1 && res2)
  }

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)
}

object SomethingSuite {
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
    override val stateFragments: Map[StateFragmentId, StateFragment] = Map {
      StateFragmentId("yes") -> StateFragment(i)
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
      StateFragmentId("yes") -> StateFragment.state(s => s.i)
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
