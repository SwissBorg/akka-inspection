package akka.inspection.main

import akka.actor.{Actor, ActorSystem, Props}
import akka.inspection.inspectable.Inspectable
import akka.inspection.inspectable.derivation.semiauto._
import akka.inspection.manager.state.Group
import akka.inspection.{Fragment, ImmutableInspection, MutableInspection}
import com.typesafe.config.{Config, ConfigFactory}

object Main {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("HELLOWORLD", testConfig)

    val a = system.actorOf(Props[StatelessActor], "stateless-actor")
    val b = system.actorOf(Props[MutableActor], "mutable-actor")
    val c = system.actorOf(Props[StatelessActor2], "stateless-actor-2")
  }

  class MutableActor extends Actor with MutableInspection {
    import MutableActor._

    private var i: State = State(1)

    override def receive: Receive = { case _ => i = State(i.i + 1) }

    override val fragments: Map[FragmentId, Fragment] = fragmentsFrom(i)

    override val groups: Set[Group] = Set(Group("hello"), Group("world"))
  }

  object MutableActor {
    final case class State(i: Int)
    object State {
      implicit val stateInspectable: Inspectable[State] = deriveInspectable
    }
  }

  class StatelessActor extends Actor with ImmutableInspection {
    override def receive: Receive = mainReceive(StatelessActor.State(0))

    def mainReceive(s: StatelessActor.State): Receive = withInspection("main")(s) {
      case _ => context.become(mainReceive(s.copy(i = s.i + 1)))
    }

    override val groups: Set[Group] = Set(Group("hello"), Group("world"))

    implicit val stateInspectable: Inspectable[StatelessActor.State] = Inspectable.from(
      Map(
        FragmentId("yes") -> Fragment.getter(_.i),
        FragmentId("no")  -> Fragment.getter(_.i + 1),
        FragmentId("bla") -> Fragment.always(1)
      )
    )
  }

  object StatelessActor {
    final case class State(i: Int)
  }

  class StatelessActor2 extends Actor with ImmutableInspection {
    import StatelessActor2._

    override def receive: Receive =
      otherReceive(StatelessActor2.State(0, A(42, List("hello", "world"), B("foo", C(true)))))

    def mainReceive(s: State): Receive = { case _ => context.become(mainReceive(s.copy(s1 = s.s1 + 1))) }

//      withInspectionS("mainReceive")(s) {
//      case _ => context.become(mainReceive(s.copy(s1 = s.s1 + 1)))
//    }

    def otherReceive(s2: State): Receive = inspect("mainReceive")(s2) //.orElse(???)

    override val groups: Set[Group] = Set(Group("world"))
  }

  object StatelessActor2 {
    final case class C(c1: Boolean)
    final case class B(b1: String, c: C)
    final case class A(a1: Int, a2: List[String], b: B)
    final case class State(s1: Int, a: A)

    object State {
      implicit val stateInspectable: Inspectable[State] = deriveInspectable
    }

    final case class State2(s: String)
    object State2 {
      implicit val stat2Inspectable: Inspectable[State2] = deriveInspectable
    }
  }

  val testConfig: Config = ConfigFactory
    .parseString {
      """
        |http.server.preview.enable-http2 = on
        |
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
        |    inspection {
        |     server {
        |       hostname = "127.0.0.1"
        |       port = 8080
        |     }
        |    }
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
        |    seed-nodes = ["akka.tcp://HELLOWORLD@127.0.0.1:2551"]
        |  }
        |}
        |
    """.stripMargin
    }
    .withFallback(ConfigFactory.load())

}
