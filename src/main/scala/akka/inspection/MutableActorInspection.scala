package akka.inspection

import akka.actor.{Actor, ActorLogging}

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

  final def inspect(name: String): Receive = inspectS(name)(())(Inspectable.unit(fragments))

  final def withInspection(r: Receive): Receive = inspect("receive").orElse(r)

  override def aroundPreStart(): Unit = {
    super.aroundPreStart()
    ActorInspector(context.system).put(self, fragments.keySet, groups)
  }
}
