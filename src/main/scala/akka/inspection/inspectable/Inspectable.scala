package akka.inspection.inspectable

import akka.inspection
import akka.inspection.Fragment.{Always, Const, State, Undefined}
import akka.inspection.{Fragment, FragmentId}

/**
 * Typeclass for `A`s that can be inspected.
 */
trait Inspectable[A] {

  /**
   * @see [[akka.inspection.Fragment]]
   */
  type Fragment = inspection.Fragment[A]
  val Fragment = new inspection.Fragment.FragmentPartiallyApplied[A]()

  /**
   * A collection of getters on the type `A` that can be accessed
   * from their id.
   */
  val fragments: Map[FragmentId, inspection.Fragment[A]]
}

object Inspectable {
  def apply[A](implicit ev: Inspectable[A]): Inspectable[A] = ev

  def from[A](fragments0: Map[FragmentId, Fragment[A]]): Inspectable[A] = new Inspectable[A] {
    override val fragments: Map[FragmentId, inspection.Fragment[A]] = fragments0
  }
}
