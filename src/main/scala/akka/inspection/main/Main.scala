package akka.inspection.main

import akka.actor.{Actor, ActorSystem, Props}
import akka.inspection.ActorInspection.FragmentId
import akka.inspection.{ActorInspection, Fragment, Inspectable, MutableActorInspection}
import akka.inspection.manager.state.Group
import com.typesafe.config.{Config, ConfigFactory}

object Main {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("HELLOWORLD", testConfig)

    (0 until 10).foreach(_ => system.actorOf(Props[StatelessActor]))
  }

  class MutableActor extends Actor with MutableActorInspection {
    private var i: Int = 0

    override def receive: Receive = withInspection {
      case _ => i += 1
    }

    override val fragments: Map[FragmentId, Fragment] = Map(
      FragmentId("yes") -> Fragment.always(i),
      FragmentId("no") -> Fragment.always(i + 1)
    )

    override val groups: Set[Group] = Set(Group("hello"), Group("world"))
  }

  class StatelessActor extends Actor with ActorInspection {
    override def receive: Receive = mainReceive(StatelessActor.State(0))

    def mainReceive(s: StatelessActor.State): Receive = withInspectionS("main")(s) {
      case _ => context.become(mainReceive(s.copy(i = s.i + 1)))
    }

    override val groups: Set[Group] = Set(Group("hello"), Group("world"))

    implicit val stateInspectable: Inspectable[StatelessActor.State] = (s: StatelessActor.State) =>
      Map(
        FragmentId("yes") -> Fragment.state(_.i),
        FragmentId("no") -> Fragment.state(_.i + 1)
    )
  }

  object StatelessActor {
    case class State(i: Int)
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
