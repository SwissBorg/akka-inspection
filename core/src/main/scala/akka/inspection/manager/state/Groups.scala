package akka.inspection.manager.state

import akka.inspection.manager.ActorInspectorManager.InspectableActorRef

/**
 * Manages the groups in which the inspectable actors belong.
 */
final private[manager] case class Groups(private val groups: Map[Group, Set[InspectableActorRef]]) extends AnyVal {
  def addGroups(ref: InspectableActorRef, groups: Set[Group]): Groups =
    copy(groups = groups.foldLeft(this.groups) {
      case (groups, group) => groups + (group -> (groups.getOrElse(group, Set.empty) + ref))
    })

  def remove(ref: InspectableActorRef): Groups =
    copy(groups = groups.map { case (group, refs) => (group, refs - ref) })

  def inGroup(group: Group): Set[InspectableActorRef] = groups.getOrElse(group, Set.empty)

  def groupsOf(ref: InspectableActorRef): Set[Group] = groups.foldLeft(Set.empty[Group]) {
    case (groups, (group, refs)) => if (refs.contains(ref)) groups + group else groups
  }
}

private[manager] object Groups {
  val empty: Groups = Groups(Map.empty[Group, Set[InspectableActorRef]])
}
