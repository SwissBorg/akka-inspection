package akka.inspection

import akka.inspection.ActorInspection.FragmentId

trait Inspectable[A] {

  /**
   * @see [[akka.inspection.Fragment]]
   */
  type Fragment = akka.inspection.Fragment[A]
  val Fragment = new akka.inspection.Fragment.FragmentPartiallyApplied[A]()

  def fragments(a: A): Map[FragmentId, akka.inspection.Fragment[A]]
}

object Inspectable {
  type Aux[A, F] = Inspectable[A] { type Fragment = F }

  def apply[A](implicit ev: Inspectable[A]): Inspectable[A] = ev

  def unit(fragments: Map[FragmentId, Fragment[Unit]]): Inspectable[Unit] = (_: Unit) => fragments
}
