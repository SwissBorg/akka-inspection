package akka.inspection

import akka.actor.{Actor, ActorLogging}
import akka.inspection.Fragment.{Always, Const, Getter, Undefined}
import akka.inspection.extension.ActorInspector
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
trait MutableInspection extends ActorInspection with ActorLogging { this: Actor =>
  type Fragment = akka.inspection.Fragment[Unit]
  val Fragment = new akka.inspection.Fragment.FragmentPartiallyApplied[Unit]()

  /**
   * [[Fragment]]s given their id.
   *
   * If the state is a product (i.e. a case class or tuple) [[fragmentsFrom()]] can be used
   * to automatically generate an implementation.
   *
   * @see [[Fragment]]
   */
  val fragments: Map[FragmentId, Fragment]

  /**
   * Creates an instance for [[fragments]] from an inspectable state `s`.
   * `s` has to be a variable for this to work.
   *
   * @param s the state for which to create the map.
   * @tparam S the type of the state.
   * @return a map with all the fragments that can be used to implemented [[fragments]].
   */
  def fragmentsFrom[S: Inspectable](s: => S): Map[FragmentId, Fragment] =
    Inspectable[S].fragments.map {
      case (id, c: Const)        => id -> c
      case (id, a: Always)       => id -> a
      case (id, Getter(fragment)) => id -> Always(() => fragment(s))
      case (id, u: Undefined)    => id -> u
    }

  // todo keep this or not?
//  /**
//   * A receive handling the inspection requests.
//   *
//   * @param state the name given to the actor's state.
//   * @return a receive handling inspection requests
//   */
//  final def inspect(state: String): Receive = inspectS(state)(())(Inspectable.from(fragments))

//  /**
//   * Adds the handling of inspections requests to `receive`.
//   *
//   * @param state the name given to the actor's state.
//   * @param receive the receive on which the handling of inspection requests will be added.
//   * @return a receive adding the handling of inspection requests to `receive`.
//   */
//  final def withInspection(state: String)(receive: Receive): Receive = inspect(state).orElse(receive)

  override def aroundPreStart(): Unit =
    ActorInspector(context.system).subscribe(self, groups)

  override def aroundReceive(receive: Receive, msg: Any): Unit =
    super.aroundReceive(handleInspectionRequests("default", (), fragments).orElse(receive), msg)
}
