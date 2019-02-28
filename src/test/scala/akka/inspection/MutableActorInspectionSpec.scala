package akka.inspection

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.inspection.ActorInspection.{FragmentsRequest => _, FragmentsResponse => _, _}
import akka.inspection.ActorInspectorImpl.InspectableActorRef
import akka.inspection.manager._
import akka.inspection.manager.state.Group
import akka.inspection.util.{LazyFuture, Render}
import akka.testkit.{ImplicitSender, TestKit}
import cats.data.OptionT
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{AsyncWordSpecLike, BeforeAndAfterAll, Matchers}

class MutableActorInspectionSpec
    extends TestKit(ActorSystem("MutableActorInspectionSpec", MutableActorInspectionSpec.testConfig))
    with ImplicitSender
    with AsyncWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Eventually {
  import MutableActorInspectionSpec._

  "MutableActorInspectionSpec" must {
    "Observe state change" in {
      val inspector = ActorInspector(system)

      val inspectableRef = InspectableActorRef(system.actorOf(Props[MutableActor]))

      val m = FragmentsRequest(List(FragmentId("yes")), inspectableRef.toId)
//      val m = GroupRequest(Group("bla"))

//      inspector.requestFragments(m).onComplete {
//        case r => println(r)
//      }

      val expectedFragment1 = Map(FragmentId("yes") -> RenderedFragment("1"))

      inspectableRef.ref ! 42

      val assertion = for {
        grpcResponse <- OptionT.liftF(LazyFuture(inspector.requestFragments(m.toGRPC)))
        response <- OptionT.fromOption[LazyFuture](FragmentsResponse.fromGRPC(grpcResponse))
      } yield
        response match {
          case FragmentsResponse(Right(fragments)) => assert(fragments == expectedFragment1)
          case r                                   => assert(false, r)
        }

      assertion
        .map(eventually(_))
        .fold(assert(false))(identity)
        .value
    }
  }

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(5, Millis)))

  override def afterAll: Unit = TestKit.shutdownActorSystem(system)
}

object MutableActorInspectionSpec {
  implicit val intShow: Render[Int] = (t: Int) => t.toString

  class MutableActor extends Actor with MutableActorInspection with ActorLogging {
    log.debug("STARTed!!!!!!!!!!!!!!!!!!!!!")

    var i: Int = 0

    override def receive: Receive = withInspection {
      case r =>
        log.debug(r.toString)
        i += 1
    }

    override val fragments: Map[FragmentId, Fragment] = Map {
      FragmentId("yes") -> Fragment.always(i)
    }
  }

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
