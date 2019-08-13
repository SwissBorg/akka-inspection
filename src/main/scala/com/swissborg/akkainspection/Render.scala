package com.swissborg.akkainspection

/**
  * Typeclass for rendering elements of type `T`.
  */
trait Render[T] {
  def render(t: T): String
}

object Render {
  def apply[T](implicit ev: Render[T]): Render[T] = ev

  implicit def render[A]: Render[A] = (t: A) => t.toString
}
