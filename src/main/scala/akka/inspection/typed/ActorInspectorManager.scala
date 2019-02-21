package akka.inspection.typed
import akka.NotUsed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.{ActorPath, ActorRef}
import akka.stream.scaladsl.Sink

object ActorInspectorManager {
  case class State(actors: Map[ActorPath, ActorRef],
                   keys: Map[ActorRef, Set[String]],
                   groups: Map[String, Set[ActorRef]],
                   streams: Map[ActorRef, Sink[ActorRef, NotUsed]])

  object State {
    val empty: State = State(actors = Map.empty, keys = Map.empty, groups = Map.empty, streams = Map.empty)
  }

  sealed abstract class SubscriptionCommand extends Product with Serializable {
    def handle(s: State): State
  }

  final case class Put(ref: ActorRef, keys: Set[String], group: String) extends SubscriptionCommand {
    override def handle(s: State): State = s match {
      case State(actors, keys0, groups, streams) =>
        State(actors + (ref.path -> ref),
              keys0 + (ref       -> keys),
              groups + (group    -> (groups.getOrElse(group, Set.empty) + ref)),
              streams + (ref     -> Sink.actorRef(ref, ())))
    }
  }

  final case class PutWithoutGroup(ref: ActorRef, keys: Set[String]) extends SubscriptionCommand {
    override def handle(s: State): State = s match {
      case State(actors, keys0, groups, streams) =>
        State(actors + (ref.path -> ref), keys0 + (ref -> keys), groups, streams + (ref -> Sink.actorRef(ref, ())))
    }
  }

  final case class Release(ref: ActorRef) extends SubscriptionCommand {
    override def handle(s: State): State =
      s.copy(actors = s.actors - ref.path, keys = s.keys - ref, groups = removeFromGroups(s.groups, ref), streams = s.streams - ref)

    private def removeFromGroups(groups: Map[String, Set[ActorRef]], ref: ActorRef): Map[String, Set[ActorRef]] =
      // TODO needs to be more efficient, what if in multiple groups?
      groups.find(_._2.contains(ref)) match {
        case Some((group, refs)) => groups + (group -> (refs - ref))
        case None                => groups
      }
  }

  def mainBehavior(s: State): Behavior[SubscriptionCommand] = Behaviors.receiveMessage(m => mainBehavior(m.handle(s)))
}
