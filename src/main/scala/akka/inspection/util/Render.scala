package akka.inspection.util

import shapeless.{:+:, ::, CNil, Cached, Coproduct, Generic, HList, HNil, Lazy, Strict}

trait Render[T] {
  def render(t: T): String
}

object Render {
  def apply[T](implicit ev: Render[T]): Render[T] = ev

  implicit def toStringRender[T]: Render[T] = (t: T) => t.toString

}

sealed trait DerivedRender[A] extends Render[A]

object DerivedRender {
  def gen[A, R](implicit G: Generic.Aux[A, R], R: Cached[Strict[DerivedRender[R]]]): DerivedRender[A] = ???

  def hcons[H, T <: HList](implicit H: Lazy[Render[H]], T: DerivedRender[T]): DerivedRender[H :: T] = ???
  def hnil: DerivedRender[HNil] = ???

  def ccons[H, T <: Coproduct](implicit H: Lazy[Render[H]], T: DerivedRender[T]): DerivedRender[H :+: T] = ???
  def cnil: DerivedRender[CNil] = ???
}
