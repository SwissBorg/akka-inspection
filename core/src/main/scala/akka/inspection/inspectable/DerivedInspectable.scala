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

  /**
   * Derive an `Inspectable[A]`.
   */
  def gen[A, Repr](implicit gen: LabelledGeneric.Aux[A, Repr],
                   inspectableRepr: Cached[Strict[DerivedInspectable[Repr]]]): DerivedInspectable[A] =
    new DerivedInspectable[A] {
      override def fragments: Map[ActorInspection.FragmentId, inspection.Fragment[A]] =
        inspectableRepr.value.value.fragments.map {
          case (id, Const(fragment))  => id -> Const[A](fragment)
          case (id, Always(fragment)) => id -> Always[A](fragment)
          case (id, State(fragment))  => id -> State[A](fragment.compose(gen.to))
          case (id, Undefined())      => id -> Undefined[A]()
        }
    }

  implicit def hconsDerivedInspectable0[K <: Symbol, H, Repr <: HList, T <: HList](
    implicit witness: Witness.Aux[K],
    gen: LabelledGeneric.Aux[H, Repr],
    derivedInspectableRepr: Lazy[DerivedInspectable[Repr]],
    derivedInspectableT: DerivedInspectable[T]
  ): DerivedInspectable[FieldType[K, H] :: T] = new DerivedInspectable[FieldType[K, H] :: T] {
    override def fragments: Map[FragmentId, inspection.Fragment[FieldType[K, H] :: T]] = {
      val fragmentsR = derivedInspectableRepr.value.fragments.map {
        case (FragmentId(id), fragment) =>
          (FragmentId(s"${witness.value.name}.$id"),
           fragment.contramap[FieldType[K, H] :: T](hcons => gen.to(hcons.head)))
      }

      derivedInspectableT.fragments.mapValues(_.contramap[FieldType[K, H] :: T](_.tail)) ++ fragmentsR
    }
  }

  implicit val hnilDerivedInspectable: DerivedInspectable[HNil] = new DerivedInspectable[HNil] {
    override def fragments: Map[FragmentId, inspection.Fragment[HNil]] =
      Map.empty[FragmentId, inspection.Fragment[HNil]]
  }
}

trait LowPriorityDerivedInspectable {
  implicit def hconsDerivedInspectable1[K <: Symbol, H, T <: HList](
    implicit witness: Witness.Aux[K],
    renderH: Lazy[Render[FieldType[K, H]]],
    derivedInspectableT: DerivedInspectable[T]
  ): DerivedInspectable[FieldType[K, H] :: T] =
    new DerivedInspectable[FieldType[K, H] :: T] {
      override def fragments: Map[ActorInspection.FragmentId, inspection.Fragment[FieldType[K, H] :: T]] =
        derivedInspectableT.fragments.map {
          case (id, fragment) =>
            (id, fragment.contramap[FieldType[K, H] :: T](_.tail))
        } + (FragmentId(
          witness.value.name
        ) -> Fragment.state(_.head)(renderH.value))
    }
}
