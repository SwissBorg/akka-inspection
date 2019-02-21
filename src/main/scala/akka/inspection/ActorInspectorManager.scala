package akka.inspection
import akka.NotUsed
import akka.actor.{Actor, ActorRef}
import akka.inspection.ActorInspectorManager.{Put, Release}
import akka.stream.scaladsl.Sink

class ActorInspectorManager extends Actor {
  var groups: Map[String, Set[ActorRef]]              = Map.empty
  var streams: Map[ActorRef, Sink[ActorRef, NotUsed]] = Map.empty

//  val l: Sink[ActorRef, NotUsed] = Sink.a

  override def receive: Receive = {
    case Put(ref, group) => groups += (group -> (groups.getOrElse(group, Set.empty) + ref))
    case Release(ref) =>
      // TODO needs to be more efficient, what if in multiple groups?
      groups.find(_._2.contains(ref)).foreach {
        case (group, refs) => groups = groups + (group -> (refs - ref))
      }
  }
}

object ActorInspectorManager {
  final case class Put(ref: ActorRef, group: String)
  final case class Release(ref: ActorRef)
}
