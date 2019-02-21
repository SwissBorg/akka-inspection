package akka.inspection.typed

import akka.actor.ActorPath
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.inspection.ActorInspectorImpl.Group
import akka.stream.scaladsl.Sink
import akka.{NotUsed, actor => untyped}

import scala.util.Try

object ActorInspectorManager {
  private case class State(actors: Map[ActorPath, untyped.ActorRef],
                           keys: Map[untyped.ActorRef, Set[String]],
                           groups: Map[String, Set[untyped.ActorRef]],
                           streams: Map[untyped.ActorRef, Sink[untyped.ActorRef, NotUsed]])

  private object State {
    val empty: State = State(actors = Map.empty, keys = Map.empty, groups = Map.empty, streams = Map.empty)
  }

  sealed abstract class Events extends Product with Serializable {
    def run(s: State): Behavior[Events]
  }

  final case class Put(ref: untyped.ActorRef, keys: Set[String], group: String) extends Events {
    override def run(s: State): Behavior[Events] = s match {
      case State(actors, keys0, groups, streams) =>
        mainBehavior(
          State(actors + (ref.path -> ref),
                keys0 + (ref       -> keys),
                groups + (group    -> (groups.getOrElse(group, Set.empty) + ref)),
                streams + (ref     -> Sink.actorRef(ref, ()))))
    }
  }

  final case class PutWithoutGroup(ref: untyped.ActorRef, keys: Set[String]) extends Events {
    override def run(s: State): Behavior[Events] = s match {
      case State(actors, keys0, groups, streams) =>
        mainBehavior(State(actors + (ref.path -> ref), keys0 + (ref -> keys), groups, streams + (ref -> Sink.actorRef(ref, ()))))
    }
  }

  final case class Release(ref: untyped.ActorRef) extends Events {
    override def run(s: State): Behavior[Events] =
      mainBehavior(s.copy(actors = s.actors - ref.path, keys = s.keys - ref, groups = removeFromGroups(s.groups, ref), streams = s.streams - ref))

    private def removeFromGroups(groups: Map[String, Set[untyped.ActorRef]], ref: untyped.ActorRef): Map[String, Set[untyped.ActorRef]] =
      // TODO needs to be more efficient, what if in multiple groups?
      groups.find(_._2.contains(ref)) match {
        case Some((group, refs)) => groups + (group -> (refs - ref))
        case None                => groups
      }
  }

  final case class QueryableActorsRequest(replyTo: ActorRef[QueryableActorsResponse]) extends Events {
    override def run(s: State): Behavior[Events] = {
      replyTo ! QueryableActorsResponse(s.actors.keys.map(_.address.toString).toList)
      Behavior.same
    }
  }

  // TODO PUT WHERE IT BELONGS
  final case class QueryableActorsResponse(queryable: List[String])

  final case class ActorGroupRequest(path: String, replyTo: ActorRef[ActorGroupResponse]) extends Events {
    override def run(s: State): Behavior[Events] = {
      val maybeGroup = for {
        path  <- Try(ActorPath.fromString(path)).toEither.left.map(t => Bla(t.getMessage)).right
        ref   <- s.actors.get(path).toRight(ActorNotFound)
        group <- s.groups.find(_._2.contains(ref)).map(_._1).toRight(GroupNotFound)
      } yield Group.Name(group)

      replyTo ! ActorGroupResponse(maybeGroup) // TODO handle this better

      Behavior.same
    }
  }

  // TODO PUT WHERE IT BELONGS
  final case class ActorGroupResponse(group: Either[Error, Group.Name])

  final case class GroupRequest(group: String, replyTo: ActorRef[GroupResponse]) extends Events {
    override def run(s: State): Behavior[Events] = {
      replyTo ! GroupResponse(s.groups.getOrElse(group, Set.empty).map(_.path))
      Behavior.same
    }
  }

  final case class GroupResponse(paths: Set[ActorPath])

  private def mainBehavior(s: State): Behavior[Events] = Behaviors.receiveMessage(_.run(s))

  val initBehavior: Behavior[Events] = Behavior.validateAsInitial(mainBehavior(State.empty)) // TODO needed?

  sealed abstract class Error       extends Product with Serializable
  final case object ActorNotFound   extends Error
  final case object GroupNotFound   extends Error
  final case class Bla(msg: String) extends Error
}
