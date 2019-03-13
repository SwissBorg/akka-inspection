package akka.inspection.util

import shapeless.labelled.FieldType

trait Render[T] {
  def render(t: T): String
}

object Render {
  def apply[T](implicit ev: Render[T]): Render[T] = ev

//  implicit def toStringRender[T]: Render[T] = (t: T) => t.toString

  implicit val intRender: Render[Int]         = (t: Int) => t.toString
  implicit val doubleRender: Render[Double]   = (t: Double) => t.toString
  implicit val floatRender: Render[Float]     = (t: Float) => t.toString
  implicit val booleanRender: Render[Boolean] = (t: Boolean) => t.toString
  implicit val charRender: Render[Char]       = (t: Char) => t.toString
  implicit val stringRender: Render[String]   = (t: String) => t.toString

  implicit def listRender[A](implicit renderA: Render[A]): Render[List[A]] =
    (t: List[A]) => t.map(renderA.render).toString

  implicit def mapRander[A, B](implicit renderA: Render[A], renderB: Render[B]): Render[Map[A, B]] =
    (t: Map[A, B]) => t.map { case (a, b) => (renderA.render(a), renderB.render(b)) }.toString // TODO looses values...

  implicit def setRender[A](implicit renderA: Render[A]): Render[Set[A]] = (t: Set[A]) => t.map(renderA.render).toString

  implicit def seqRender[A](implicit renderA: Render[A]): Render[Seq[A]] = (t: Seq[A]) => t.map(renderA.render).toString

  implicit def fieldTypeRender[K, V](implicit renderV: Render[V]): Render[FieldType[K, V]] = renderV.render
}
