package com.swissborg.akkainspection.inspectable

import com.swissborg.akkainspection
import com.swissborg.akkainspection.{Fragment, FragmentId}

/**
  * Typeclass for `A`s that can be inspected.
  */
trait Inspectable[A] {

  /**
    * @see [[com.swissborg.akkainspection.Fragment]]
    */
  type Fragment = akkainspection.Fragment[A]
  val Fragment = new akkainspection.Fragment.FragmentPartiallyApplied[A]()

  /**
    * A collection of getters on the type `A` that can be accessed
    * from their id.
    */
  val fragments: Map[FragmentId, akkainspection.Fragment[A]]
}

object Inspectable {
  def apply[A](implicit ev: Inspectable[A]): Inspectable[A] = ev

  def from[A](fragments0: Map[FragmentId, Fragment[A]]): Inspectable[A] =
    new Inspectable[A] {
      override val fragments: Map[FragmentId, akkainspection.Fragment[A]] =
        fragments0
    }
}
