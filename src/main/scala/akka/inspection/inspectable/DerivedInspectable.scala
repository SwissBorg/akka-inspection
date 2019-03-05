package akka.inspection.inspectable

import akka.inspection
import akka.inspection.ActorInspection
import akka.inspection.ActorInspection.FragmentId
import akka.inspection.Fragment._
import akka.inspection.util.Render
import cats.implicits._
import cats.{Always => _}
import shapeless.labelled.FieldType
import shapeless.{::, Cached, HList, HNil, LabelledGeneric, Lazy, Strict, Witness}

sealed trait DerivedInspectable[A] extends Inspectable[A]

object DerivedInspectable extends LowPriorityDerivedInspectable {
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

  implicit def hconsDerivedInspectable0[K <: Symbol, H, T <: HList](
    implicit K: Witness.Aux[K],
    H: Lazy[Render[FieldType[K, H]]],
    T: DerivedInspectable[T]
  ): DerivedInspectable[FieldType[K, H] :: T] =
    new DerivedInspectable[FieldType[K, H] :: T] {
      override def fragments: Map[ActorInspection.FragmentId, inspection.Fragment[FieldType[K, H] :: T]] =
        // TODO MAP!!
        T.fragments.mapValues(
          _.contramap[FieldType[K, H] :: T](_.tail)
        ) + (FragmentId(K.value.name) -> Fragment.state(_.head)(H.value))
    }

  implicit val hnilDerivedInspectable: DerivedInspectable[HNil] = new DerivedInspectable[HNil] {
    override def fragments: Map[FragmentId, inspection.Fragment[HNil]] = Map.empty
  }
}

trait LowPriorityDerivedInspectable {
  // TODO nested doesn't work :/ never used
  implicit def hconsDerivedInspectable1[K <: Symbol, H, R <: HList, T <: HList](
    implicit K: Witness.Aux[K],
    G: LabelledGeneric.Aux[H, R],
    R: Lazy[DerivedInspectable[R]],
    T: DerivedInspectable[T]
  ): Inspectable[FieldType[K, H] :: T] = new Inspectable[FieldType[K, H] :: T] {
    override def fragments: Map[FragmentId, inspection.Fragment[FieldType[K, H] :: T]] = {
      val fragmentsR = R.value.fragments.map {
        case (FragmentId(id), fragment) =>
          (FragmentId(s"${K.value.name}.$id"), fragment.contramap[FieldType[K, H] :: T](hcons => G.to(hcons.head)))
      }

      T.fragments.mapValues(_.contramap[FieldType[K, H] :: T](_.tail)) ++ fragmentsR
    }
  }

}
