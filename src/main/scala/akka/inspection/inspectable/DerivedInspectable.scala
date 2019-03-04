package akka.inspection.inspectable

import akka.inspection
import akka.inspection.ActorInspection
import akka.inspection.ActorInspection.FragmentId
import akka.inspection.Fragment._
import akka.inspection.inspectable.DerivedInspectable.{Bar, Baz}
import akka.inspection.util.Render
import cats.implicits._
import cats.{Always => _}
import shapeless.labelled.FieldType
import shapeless.{::, Cached, HList, HNil, LabelledGeneric, Lazy, Strict, Witness}

sealed trait DerivedInspectable[A] extends Inspectable[A]

object Bla extends App {
  val baz: Inspectable[Baz] = DerivedInspectable.gen
  println(baz.fragments)
  println(baz.fragments(FragmentId("bar")).run(Baz(Bar(5, List(1, 2, 3)))))
}

object DerivedInspectable {
  case class Bla()
  case class Bar(i: Int, l: List[Int])
  case class Baz(bar: Bar)

  val bla: Inspectable[Bla] = DerivedInspectable.gen
  val bar: Inspectable[Bar] = DerivedInspectable.gen

  def gen[A, R](implicit G: LabelledGeneric.Aux[A, R],
                R: Cached[Strict[DerivedInspectable[R]]]): DerivedInspectable[A] =
    new DerivedInspectable[A] {
      override def fragments: Map[ActorInspection.FragmentId, inspection.Fragment[A]] =
        R.value.value.fragments.mapValues {
          case Fix(fragment)    => Fix(fragment)
          case Always(fragment) => Always(fragment)
          case Given(fragment)  => Given(fragment.compose(G.to))
          case Undefined()      => Undefined()
        }
    }

  implicit def hcons[K <: Symbol, H, T <: HList](implicit K: Witness.Aux[K],
                                                 H: Lazy[Render[FieldType[K, H]]],
                                                 T: DerivedInspectable[T]): DerivedInspectable[FieldType[K, H] :: T] =
    new DerivedInspectable[FieldType[K, H] :: T] {
      override def fragments: Map[ActorInspection.FragmentId, inspection.Fragment[FieldType[K, H] :: T]] =
        // TODO MAP!!
        T.fragments.mapValues(
          _.contramap[FieldType[K, H] :: T](_.tail)
        ) + (FragmentId(K.value.name) -> Fragment.state(_.head)(H.value))
    }

  implicit val hnil: DerivedInspectable[HNil] = new DerivedInspectable[HNil] {
    override def fragments: Map[FragmentId, inspection.Fragment[HNil]] = Map.empty
  }
}
