package akka.inspection

import akka.actor.{Actor, ActorLogging}
import akka.inspection.inspectable.Inspectable

/**
 * Adds the ability to inspect the actor from outside the cluster.
 *
 * This trait is designed for actors whose state is transformed by mutating it.
 * Use [[ActorInspection]] for actors whose state is transformed using `context.become(someReceive(State))`
  **
 * To do this the existent receive methods have to wrapped with `withInspection` or `inspect` has to
 * be composed with the existing ones.
 */
trait MutableActorInspection extends ActorInspection with ActorLogging { this: Actor =>
  type FragmentId = ActorInspection.FragmentId

  type Fragment = akka.inspection.Fragment[Unit]
  val Fragment = new akka.inspection.Fragment.FragmentPartiallyApplied[Unit]()

  /**
   * [[Fragment]]s given their id.
   *
   * @see [[Fragment]]
   */
  val fragments: Map[FragmentId, Fragment]

  /**
   * A receive handling the inspection requests.
   *
   * @param state the name given to the actor's state.
   * @return a receive handling inspection requests
   */
  final def inspect(state: String): Receive = inspectS(state)(())(Inspectable.from(fragments))

  /**
   * Adds the handling of inspections requests to `receive`.
   *
   * @param state the name given to the actor's state.
   * @param receive the receive on which the handling of inspection requests will be added.
   * @return a receive adding the handling of inspection requests to `receive`.
   */
  final def withInspection(state: String)(receive: Receive): Receive = inspect(state).orElse(receive)

  override def aroundPreStart(): Unit =
    ActorInspector(context.system).subscribe(self, groups)
}
