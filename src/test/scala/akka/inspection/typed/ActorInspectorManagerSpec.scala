package akka.inspection.typed

import akka.actor.testkit.typed.javadsl.TestInbox
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.inspection.ActorInspectorImpl.Group
import akka.inspection.typed.ActorInspectorManager.{ActorGroupRequest, ActorGroupResponse, Put}
import org.scalatest.{Matchers, WordSpec}

class ActorInspectorManagerSpec extends WordSpec with Matchers {

  "ActorInspectorManager" must {
    "bla" in {
      val testKit = BehaviorTestKit(ActorInspectorManager.Events.init)
      val inbox   = TestInbox.create[ActorGroupResponse]()

      val untypedRef = TestInbox.create[Any]().getRef().toUntyped

      testKit.run(Put(untypedRef, Set.empty, "hello"))
      testKit.run(ActorGroupRequest(untypedRef.path.address.toString, inbox.getRef()))
      inbox.expectMessage(ActorGroupResponse(Right(Group.Name("hello"))))
    }
  }
}

object ActorInspectorManagerSpec {
//  class NopActor extends untyped.Actor {
//    override def receive: Receive = {
//      case _ => ()
//    }
//  }
}
