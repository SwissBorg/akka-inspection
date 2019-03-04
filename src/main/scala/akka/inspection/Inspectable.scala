package akka.inspection

import akka.inspection
import akka.inspection.ActorInspection.FragmentId

trait Inspectable[A] {

  /**
   * @see [[akka.inspection.Fragment]]
   */
  type Fragment = akka.inspection.Fragment[A]
  val Fragment = new akka.inspection.Fragment.FragmentPartiallyApplied[A]()

  def fragments: Map[FragmentId, akka.inspection.Fragment[A]]
}

object Inspectable {
  type Aux[A, F] = Inspectable[A] { type Fragment = F }

  def apply[A](implicit ev: Inspectable[A]): Inspectable[A] = ev

  def from[A](fragments0: Map[FragmentId, Fragment[A]]): Inspectable[A] = new Inspectable[A] {
    override def fragments: Map[FragmentId, inspection.Fragment[A]] = fragments0
  }
}
