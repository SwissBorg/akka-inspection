package akka.inspection.util

import shapeless.labelled.FieldType

trait Render[T] {
  def render(t: T): String
}

object Render {
  def apply[T](implicit ev: Render[T]): Render[T] = ev

//  implicit def toStringRender[T]: Render[T] = (t: T) => t.toString

  implicit val intRender: Render[Int] = (t: Int) => t.toString
  implicit def listRender[A](implicit R: Render[A]): Render[List[A]] = (t: List[A]) => t.map(R.render).toString

  implicit def fieldTypeRender[K, V](implicit R: Render[V]): Render[FieldType[K, V]] = R.render
}

sealed trait DerivedRender[A] extends Render[A]

object DerivedRender {
//  def gen[A, R](implicit G: Generic.Aux[A, R], R: Cached[Strict[DerivedRender[R]]]): DerivedRender[A] = ???
//
//  def hcons[H, T <: HList](implicit H: Lazy[Render[H]], T: DerivedRender[T]): DerivedRender[H :: T] = ???
//  def hnil: DerivedRender[HNil] = ???
//
//  def ccons[H, T <: Coproduct](implicit H: Lazy[Render[H]], T: DerivedRender[T]): DerivedRender[H :+: T] = ???
//  def cnil: DerivedRender[CNil] = ???
}
