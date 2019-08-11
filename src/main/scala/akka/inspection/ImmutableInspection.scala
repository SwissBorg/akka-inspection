package akka.inspection

import akka.actor.{Actor, ActorLogging}
import akka.inspection.inspectable.Inspectable

/**
 * Adds the ability to inspect the actor from outside the cluster.
 *
 * This trait is designed for actors whose state is transformed using `context.become(someReceive(state))`.
 * Use [[MutableInspection]] for actors whose state is transformed by mutating it.
 *
 * To do this the existent receive methods have to wrapped with `withInspection` or `inspect` has to
 * be composed with the existing ones.
 * These methods rely on instance of the [[Inspectable]] typeclass. An instance can be automatically
 * derived for product type (i.e. case classes and tuples). Use `deriveInspectable`
 * to get the instance. If needed, it can also be manually be implemented.
 *
 */
trait ImmutableInspection extends ActorInspection with ActorLogging { this: Actor =>

  /**
   * Handles inspection requests.
   *
   * @param state the name given to the current state.
   * @param s the state.
   * @tparam S the type of the state.
   * @return a receive that handles inspection requests.
   */
  final def inspect[S: Inspectable](state: String)(s: S): Receive =
    handleInspectionRequests(state, s, Inspectable[S].fragments)

  /**
   * Adds handling of inspection requests to `r`.
   *
   * @param state the name given to the current state.
   * @param s the state.
   * @param receive the receive being wrapped.
   * @tparam S the type of the state
   * @return a receive adding the handling of inspection requests to `receive`.
   */
  final def withInspection[S: Inspectable](state: String)(s: S)(receive: Receive): Receive =
    inspect(state)(s).orElse(receive)
}
