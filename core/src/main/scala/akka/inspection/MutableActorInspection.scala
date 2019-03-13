package akka.inspection

import akka.actor.{Actor, ActorLogging}
import akka.inspection.inspectable.Inspectable

/**
 * Mix-in to make the actor available for inspection.
 */
trait MutableActorInspection extends ActorInspection with ActorLogging { this: Actor =>

  /**
   * @see [[ActorInspection.FragmentId]]
   */
  type FragmentId = ActorInspection.FragmentId

  /**
   * @see [[akka.inspection.Fragment]]
   */
  type Fragment = akka.inspection.Fragment[Unit]
  val Fragment = new akka.inspection.Fragment.FragmentPartiallyApplied[Unit]()

  /**
   * [[Fragment]]s given their id.
   */
  val fragments: Map[FragmentId, Fragment]

  final def inspect(name: String): Receive = inspectS(name)(())(Inspectable.from(fragments))

  final def withInspection(name: String)(r: Receive): Receive = inspect(name).orElse(r)

  override def aroundPreStart(): Unit =
    ActorInspector(context.system).put(self, fragments.keySet, groups)
}
