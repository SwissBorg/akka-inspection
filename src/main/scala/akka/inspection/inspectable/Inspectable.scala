package akka.inspection.inspectable

import akka.inspection
import akka.inspection.ActorInspection.FragmentId
import akka.inspection.Fragment
import cats._
import cats.implicits._

trait Inspectable[A] {

  /**
   * @see [[akka.inspection.Fragment]]
   */
  type Fragment = inspection.Fragment[A]
  val Fragment = new inspection.Fragment.FragmentPartiallyApplied[A]()

  def fragments: Map[FragmentId, inspection.Fragment[A]]
}

object Inspectable {
  def apply[A](implicit ev: Inspectable[A]): Inspectable[A] = ev

  def from[A](fragments0: Map[FragmentId, Fragment[A]]): Inspectable[A] = new Inspectable[A] {
    override def fragments: Map[FragmentId, inspection.Fragment[A]] = fragments0
  }

  implicit def pair[A, B](implicit ia: Inspectable[A], ib: Inspectable[B]): Inspectable[(A, B)] =
    Semigroupal[Inspectable].product(ia, ib)

  implicit def inspectableContravariantMonoidal: ContravariantMonoidal[Inspectable] =
    new ContravariantMonoidal[Inspectable] {
      override def contramap[A, B](fa: Inspectable[A])(f: B => A): Inspectable[B] = new Inspectable[B] {
        override def fragments: Map[FragmentId, inspection.Fragment[B]] = fa.fragments.map {
          case (id, fragment) => (id, fragment.contramap(f))
        }
      }

      override def unit: Inspectable[Unit] = Inspectable.from(Map.empty)

      override def product[A, B](fa: Inspectable[A], fb: Inspectable[B]): Inspectable[(A, B)] =
        new Inspectable[(A, B)] {
          override def fragments: Map[FragmentId, inspection.Fragment[(A, B)]] = {
            val fragmentsA = fa.fragments
            val fragmentsB = fb.fragments

            fragmentsA.foldLeft(Map.empty[FragmentId, inspection.Fragment[(A, B)]]) {
              case (fragmentsAB, (id, fragmentA)) =>
                fragmentsAB + (id -> fragmentsB
                  .get(id)
                  .fold[inspection.Fragment[(A, B)]](fragmentA.contramap(_._1))(
                    fragmentB => Semigroupal[inspection.Fragment].product(fragmentA, fragmentB)
                  ))
            }
          }
        }
    }

//  implicit def inspectableAlternative: Alternative[Inspectable] = new Alternative[Inspectable] {
//    override def empty[A]: Inspectable[A] = Inspectable.from(Map.empty)
//
//    override def combineK[A](x: Inspectable[A], y: Inspectable[A]): Inspectable[A] = new Inspectable[A] {
//      override def fragments: Map[FragmentId, inspection.Fragment[A]] = x.fragments ++ y.fragments
//    }
//
//    override def pure[A](x: A): Inspectable[A] = ???
//
//    override def ap[A, B](ff: Inspectable[A => B])(fa: Inspectable[A]): Inspectable[B] = new Inspectable[B] {
//      override def fragments: Map[FragmentId, inspection.Fragment[B]] = ff.
//    }
//  }
}
