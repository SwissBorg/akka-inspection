package akka.inspection

import akka.NotUsed
import akka.actor.{Actor, ActorPath, ActorRef}
import akka.inspection.ActorInspectorImpl.Group
import akka.inspection.ActorInspectorManager.State
import akka.stream.scaladsl.Sink

import scala.util.Try

class ActorInspectorManager extends Actor {
  import ActorInspectorManager._

  override def receive: Receive = mainReceive(State.empty)

  def mainReceive(s: State): Receive = {
    val State(actors, keys0, groups, streams) = s

    {
      case Put(ref, keys, group) =>
        context.become(
          mainReceive(
            State(actors + (ref.path -> ref),
                  keys0 + (ref       -> keys),
                  groups + (group    -> (groups.getOrElse(group, Set.empty) + ref)),
                  streams + (ref     -> Sink.actorRef(ref, ())))))

      case PutWithoutGroup(ref, keys) =>
        context.become(mainReceive(State(actors + (ref.path -> ref), keys0 + (ref -> keys), groups, streams + (ref -> Sink.actorRef(ref, ())))))

      case Release(ref) =>
        context.become(mainReceive(State(actors - ref.path, keys0 - ref, removeFromGroups(groups, ref), streams - ref)))

      case _: QueryableActorsRequest.type =>
        sender() ! QueryableActorsResponse(actors.keys.map(_.address.toString).toList) // TODO DO NOT TO STRING DUMBASS

      case ActorGroupRequest(path) =>
        val maybeGroup = for {
          path  <- Try(ActorPath.fromString(path)).toEither.left.map(t => Bla(t.getMessage)).right
          ref   <- actors.get(path).toRight(ActorNotFound)
          group <- groups.find(_._2.contains(ref)).map(_._1).toRight(GroupNotFound)
        } yield Group.Name(group)

        sender() ! ActorGroupResponse(maybeGroup) // TODO handle this better

      case GroupRequest(group) =>
        sender() ! GroupResponse(groups.getOrElse(group, Set.empty).map(_.path))
    }
  }

  def changeState(f: State => Receive)(s: State): Unit = context.become(f(s))

  private def removeFromGroups(groups: Map[String, Set[ActorRef]], ref: ActorRef): Map[String, Set[ActorRef]] =
    // TODO needs to be more efficient, what if in multiple groups?
    groups.find(_._2.contains(ref)) match {
      case Some((group, refs)) => groups + (group -> (refs - ref))
      case None                => groups
    }

}

object ActorInspectorManager {
  case class State(actors: Map[ActorPath, ActorRef],
                   keys: Map[ActorRef, Set[String]],
                   groups: Map[String, Set[ActorRef]],
                   streams: Map[ActorRef, Sink[ActorRef, NotUsed]])

  object State {
    val empty: State = State(actors = Map.empty, keys = Map.empty, groups = Map.empty, streams = Map.empty)
  }

  sealed abstract class Events extends Product with Serializable

  final case class Put(ref: ActorRef, keys: Set[String], group: String) extends Events
  final case class PutWithoutGroup(ref: ActorRef, keys: Set[String])    extends Events
  final case class Release(ref: ActorRef)                               extends Events

  final case object QueryableActorsRequest extends Events

  // TODO PUT WHERE IT BELONGS
  final case class QueryableActorsResponse(queryable: List[String])

  final case class ActorGroupRequest(path: String) extends Events

  // TODO PUT WHERE IT BELONGS
  final case class ActorGroupResponse(group: Either[Error, Group.Name])

  final case class GroupRequest(group: String) extends Events

  // TODO PUT WHERE IT BELONGS
  final case class GroupResponse(paths: Set[ActorPath])

  // TODO move
  sealed abstract class Error       extends Product with Serializable
  final case object ActorNotFound   extends Error
  final case object GroupNotFound   extends Error
  final case class Bla(msg: String) extends Error
}
