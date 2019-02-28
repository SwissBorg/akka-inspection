package akka.inspection.manager.state

import akka.inspection.ActorInspection.FragmentId
import akka.inspection.manager.ActorInspectorManager.InspectableActorRef

/**
 * Manages the state-fragments of each inspectable actor.
 */
final private[manager] case class Fragments(private val fragmentIds: Map[InspectableActorRef, Set[FragmentId]])
    extends AnyVal {
  def addStateFragment(ref: InspectableActorRef, keys: Set[FragmentId]): Fragments =
    copy(fragmentIds = this.fragmentIds + (ref -> keys))
  def remove(ref: InspectableActorRef): Fragments = copy(fragmentIds = fragmentIds - ref)
  def stateFragmentsIds(ref: InspectableActorRef): Set[FragmentId] = fragmentIds.getOrElse(ref, Set.empty)
}

private[manager] object Fragments {
  val empty: Fragments = Fragments(Map.empty)
}
