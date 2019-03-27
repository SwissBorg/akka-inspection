package akka.inspection

import shapeless.labelled.FieldType

/**
 * Typeclass for rendering elements of type `T`.
 *
 * todo could use [[cats.Show]]
 */
trait Render[T] {
  def render(t: T): String
}

object Render {
  def apply[T](implicit ev: Render[T]): Render[T] = ev

  implicit def render[A]: Render[A] = (t: A) => t.toString

//  implicit val intRender: Render[Int]         = (t: Int) => t.toString
//  implicit val doubleRender: Render[Double]   = (t: Double) => t.toString
//  implicit val floatRender: Render[Float]     = (t: Float) => t.toString
//  implicit val booleanRender: Render[Boolean] = (t: Boolean) => t.toString
//  implicit val charRender: Render[Char]       = (t: Char) => t.toString
//  implicit val stringRender: Render[String]   = (t: String) => t.toString
//
//  implicit def optionRender[A](implicit A: Render[A]): Render[Option[A]] = (t: Option[A]) => t.map(A.render).toString
//
//  implicit def listRender[A](implicit renderA: Render[A]): Render[List[A]] =
//    (t: List[A]) => t.map(renderA.render).toString
//
//  implicit def mapRender[A, B](implicit renderA: Render[A], renderB: Render[B]): Render[Map[A, B]] =
//    (t: Map[A, B]) => t.toList.map { case (a, b) => (renderA.render(a), renderB.render(b)) }.toString
//
//  implicit def setRender[A](implicit renderA: Render[A]): Render[Set[A]] = (t: Set[A]) => t.map(renderA.render).toString
//
//  implicit def seqRender[A](implicit renderA: Render[A]): Render[Seq[A]] = (t: Seq[A]) => t.map(renderA.render).toString
//
//  implicit def fieldTypeRender[K, V](implicit renderV: Render[V]): Render[FieldType[K, V]] = renderV.render
}
