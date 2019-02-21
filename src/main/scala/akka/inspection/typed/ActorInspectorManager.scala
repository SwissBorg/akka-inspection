package akka.inspection.typed

import akka.actor.ActorPath
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.inspection.ActorInspectorImpl.Group
import akka.stream.scaladsl.Sink
import akka.{NotUsed, actor => untyped}

import scala.util.Try

object ActorInspectorManager {
  case class State(actors: Map[ActorPath, untyped.ActorRef],
                   keys: Map[untyped.ActorRef, Set[String]],
                   groups: Map[String, Set[untyped.ActorRef]],
                   streams: Map[untyped.ActorRef, Sink[untyped.ActorRef, NotUsed]])

  object State {
    val empty: State = State(actors = Map.empty, keys = Map.empty, groups = Map.empty, streams = Map.empty)
  }

  sealed abstract class Events extends Product with Serializable

  object Events {
    val init: Behavior[Events] = behavior(State.empty)

    def behavior(s: State): Behavior[Events] = s match {
      case State(actors, keys0, groups, streams) =>
        Behaviors.receiveMessage {
          case Put(ref, keys, group) =>
            behavior(
              State(actors + (ref.path -> ref),
                    keys0 + (ref       -> keys),
                    groups + (group    -> (groups.getOrElse(group, Set.empty) + ref)),
                    streams + (ref     -> Sink.actorRef(ref, ()))))

          case PutWithoutGroup(ref, keys) =>
            behavior(State(actors + (ref.path -> ref), keys0 + (ref -> keys), groups, streams + (ref -> Sink.actorRef(ref, ()))))

          case Release(ref) =>
            behavior(State(actors - ref.path, keys0 - ref, removeFromGroups(groups, ref), streams - ref))

          case QueryableActorsRequest(replyTo) =>
            replyTo ! QueryableActorsResponse(actors.keys.map(_.address.toString).toList)
            Behavior.same

          case ActorGroupRequest(path, replyTo) =>
            val maybeGroup = for {
              path  <- Try(ActorPath.fromString(path)).toEither.left.map(t => Bla(t.getMessage)).right
              ref   <- actors.get(path).toRight(ActorNotFound)
              group <- groups.find(_._2.contains(ref)).map(_._1).toRight(GroupNotFound)
            } yield Group.Name(group)

            replyTo ! ActorGroupResponse(maybeGroup) // TODO handle this better

            Behavior.same

          case GroupRequest(group, replyTo) =>
            replyTo ! GroupResponse(groups.getOrElse(group, Set.empty).map(_.path))
            Behavior.same
        }
    }

    private def removeFromGroups(groups: Map[String, Set[untyped.ActorRef]], ref: untyped.ActorRef): Map[String, Set[untyped.ActorRef]] =
      // TODO needs to be more efficient, what if in multiple groups?
      groups.find(_._2.contains(ref)) match {
        case Some((group, refs)) => groups + (group -> (refs - ref))
        case None                => groups
      }

  }

  final case class Put(ref: untyped.ActorRef, keys: Set[String], group: String) extends Events
  final case class PutWithoutGroup(ref: untyped.ActorRef, keys: Set[String])    extends Events
  final case class Release(ref: untyped.ActorRef)                               extends Events

  final case class QueryableActorsRequest(replyTo: ActorRef[QueryableActorsResponse]) extends Events

  // TODO PUT WHERE IT BELONGS
  final case class QueryableActorsResponse(queryable: List[String])

  final case class ActorGroupRequest(path: String, replyTo: ActorRef[ActorGroupResponse]) extends Events

  // TODO PUT WHERE IT BELONGS
  final case class ActorGroupResponse(group: Either[Error, Group.Name])

  final case class GroupRequest(group: String, replyTo: ActorRef[GroupResponse]) extends Events

  // TODO PUT WHERE IT BELONGS
  final case class GroupResponse(paths: Set[ActorPath])

  // TODO move
  sealed abstract class Error       extends Product with Serializable
  final case object ActorNotFound   extends Error
  final case object GroupNotFound   extends Error
  final case class Bla(msg: String) extends Error
}

//case class StatefulBehavior[S, A](ba: S => Behavior[A]) {
//  def compose[B](f: (S, A) => Behavior[A]): StatefulBehavior[S, A] =
//    StatefulBehavior(s => Behaviors.receiveMessage(a => ba(f(s, a))))
//
//  def run(s: S): Behavior[A] = ba(s)
//}
//
//object StatefulBehavior {
//  def empty[S, A]: StatefulBehavior[S, A] = StatefulBehavior(_ => Behaviors.empty[A])
//}
