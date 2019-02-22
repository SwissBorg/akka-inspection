package akka.inspection

import akka.NotUsed
import akka.actor.{Actor, ActorPath, ActorRef}
import akka.inspection.ActorInspectorManager.Groups.Group
import akka.inspection.ActorInspectorManager.Keys.Key
import akka.inspection.util.ActorRefUtil._
import akka.stream.scaladsl.Sink
import akka.inspection.ActorInspection._
import akka.inspection.ActorInspectorManager.InspectableActors.InspectableActorRef

class ActorInspectorManager extends Actor {
  import ActorInspectorManager._

  override def receive: Receive = mainReceive(State.empty)

  def mainReceive(s: State): Receive = {
    val State(inspectableActors, keys, groups, _) = s

    {
      case Put(ref, keys0, groups0) => context.become(mainReceive(s.put(ref, keys0, groups0)))
      case Release(ref)             => context.become(mainReceive(s.release(ref)))

      case _: QueryableActorsRequest.type =>
        sender() ! QueryableActorsResponse(inspectableActors.actorRefs.map(ref => asString(ref.ref)))

      case ActorGroupsRequest(path) =>
        sender() ! ActorGroupsResponse(inspectableActors.fromString(path).map(groups.groupsOf))

      case ActorKeysRequest(path) =>
        sender() ! ActorKeysResponse(inspectableActors.fromString(path).map(keys.keysOf))

      case GroupRequest(group) =>
        sender() ! GroupResponse(groups.inGroup(group))
    }
  }

  def changeState(f: State => Receive)(s: State): Unit = context.become(f(s))
}

object ActorInspectorManager {
  case class State(inspectableActors: InspectableActors, keys: Keys, groups: Groups, streams: Streams) {
    def put(ref: ActorRef, keys: Set[Key], groups: Set[Group]): State = {
      val inspectableActors0 = inspectableActors.add(ref)
      val inspectableRef     = inspectableActors0.fromRef(ref).right.get // We can `.get` it since we just added it.

      copy(inspectableActors = inspectableActors0,
           this.keys.addKeys(inspectableRef, keys),
           this.groups.addGroups(inspectableRef, groups),
           streams.add(inspectableRef))
    }

    def release(ref: ActorRef): State =
      inspectableActors
        .fromRef(ref)
        .map { inspectableRef =>
          copy(inspectableActors = inspectableActors.remove(inspectableRef),
               keys.remove(inspectableRef),
               groups.remove(inspectableRef),
               streams.remove(inspectableRef))
        }
        .getOrElse(this) // TODO do nothing or raise some error?
  }

  object State {
    val empty: State = State(inspectableActors = InspectableActors.empty, keys = Keys.empty, groups = Groups.empty, streams = Streams.empty)
  }

  final case class InspectableActors(private val actors: Set[InspectableActorRef]) extends AnyVal {
    def add(ref: ActorRef): InspectableActors               = copy(actors = actors + InspectableActorRef(ref))
    def remove(ref: InspectableActorRef): InspectableActors = copy(actors = actors - ref)

    def actorRefs: Set[InspectableActorRef] = actors

    def fromRef(ref: ActorRef): Either[ActorNotInspectable.type, InspectableActorRef] =
      actors.find(_.ref == ref).toRight(ActorNotInspectable)

    def fromString(s: String): Either[ActorNotInspectable.type, InspectableActorRef] =
      actors.find(ref => asString(ref.ref) == s).toRight(ActorNotInspectable)
  }

  object InspectableActors {
    sealed abstract case class InspectableActorRef(ref: ActorRef)
    private object InspectableActorRef {
      def apply(ref: ActorRef): InspectableActorRef = new InspectableActorRef(ref) {}
    }

    val empty: InspectableActors = InspectableActors(Set.empty)
  }

  final case class Groups(private val groups: Map[Group, Set[InspectableActorRef]]) extends AnyVal {
    def addGroups(ref: InspectableActorRef, groups: Set[Group]): Groups =
      copy(groups = groups.foldLeft(this.groups) {
        case (groups, group) => groups + (group -> (groups.getOrElse(group, Set.empty) + ref))
      })

    // TODO use mapValues?
    def remove(ref: InspectableActorRef): Groups =
      copy(groups = groups.map { case (group, refs) => (group, refs - ref) })

    def inGroup(group: Group): Set[InspectableActorRef] = groups.getOrElse(group, Set.empty)

    def groupsOf(ref: InspectableActorRef): Set[Group] = groups.foldLeft(Set.empty[Group]) {
      case (groups, (group, refs)) => if (refs.contains(ref)) groups + group else groups
    }
  }

  object Groups {
    final case class Group(name: String) extends AnyVal

    val empty: Groups = Groups(Map.empty)
  }

  final case class Keys(private val keys: Map[InspectableActorRef, Set[Key]]) extends AnyVal {
    def addKeys(ref: InspectableActorRef, keys: Set[Key]): Keys = copy(keys = this.keys + (ref -> keys))
    def remove(ref: InspectableActorRef): Keys                  = copy(keys = keys - ref)

    def keysOf(ref: InspectableActorRef): Set[Key] = keys.getOrElse(ref, Set.empty)
  }

  object Keys {
    final case class Key(value: String) extends AnyVal

    val empty: Keys = Keys(Map.empty)
  }

  final case class Streams(private val streams: Map[InspectableActorRef, Sink[ActorRef, NotUsed]]) extends AnyVal {
    def add(ref: InspectableActorRef): Streams    = copy(streams = streams + (ref -> Sink.actorRefWithAck(ref.ref, Init, Ack, Complete)))
    def remove(ref: InspectableActorRef): Streams = copy(streams = streams - ref)
  }

  object Streams {
    val empty: Streams = Streams(Map.empty)
  }

  sealed abstract class Event extends Product with Serializable

  final case class Put(ref: ActorRef, keys: Set[Key], groups: Set[Group]) extends Event
  final case class Release(ref: ActorRef)                                 extends Event

  final case object QueryableActorsRequest                         extends Event
  final case class QueryableActorsResponse(queryable: Set[String]) extends Event

  final case class ActorGroupsRequest(path: String)                                         extends Event
  final case class ActorGroupsResponse(group: Either[ActorNotInspectable.type, Set[Group]]) extends Event

  final case class GroupRequest(group: Group)                     extends Event
  final case class GroupResponse(paths: Set[InspectableActorRef]) extends Event

  final case class ActorKeysRequest(path: String)                                      extends Event
  final case class ActorKeysResponse(keys: Either[ActorNotInspectable.type, Set[Key]]) extends Event

  // TODO move
  sealed abstract class Error           extends Product with Serializable
  final case object ActorNotInspectable extends Error
}
