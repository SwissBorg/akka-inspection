package akka.inspection.inspectable.derivation

import akka.inspection
import akka.inspection.Fragment._
import akka.inspection.{FragmentId, Render}
import akka.inspection.inspectable.Inspectable
import cats.implicits._
import cats.{Always => _}
import shapeless.labelled.FieldType
import shapeless.{::, Cached, HList, HNil, LabelledGeneric, Lazy, Strict, Witness}

/**
  * Instance of `Inspectable` that has been derived.
  */
sealed abstract class DerivedInspectable[A] extends Inspectable[A]

object DerivedInspectable extends LowPriorityDerivedInspectable1 {

  /**
    * Derive an `Inspectable[A]` that has a fragment per leaf of `A` and whose
    * name are their absolute path separated by dots.
    */
  implicit def gen[A, Repr](
      implicit gen: LabelledGeneric.Aux[A, Repr],
      inspectableRepr: Cached[Strict[DerivedInspectable[Repr]]]
  ): DerivedInspectable[A] =
    new DerivedInspectable[A] {
      override val fragments: Map[FragmentId, inspection.Fragment[A]] =
        inspectableRepr.value.value.fragments.map {
          case (id, Getter(fragment)) => id -> Getter[A](fragment.compose(gen.to))
          case (id, c: Const)         => id -> c
          case (id, a: Always)        => id -> a
          case (id, u: Undefined)     => id -> u
        }
    }

  implicit def hconsDerivedInspectable[K <: Symbol, H, T <: HList](
      implicit witness: Witness.Aux[K],
      inspectableH: Lazy[Inspectable[H]],
      derivedInspectableT: DerivedInspectable[T]
  ): DerivedInspectable[FieldType[K, H] :: T] =
    new DerivedInspectable[FieldType[K, H] :: T] {
      override val fragments: Map[FragmentId, inspection.Fragment[FieldType[K, H] :: T]] = {
        val fragmentsR = inspectableH.value.fragments.map {
          case (FragmentId(id), fragment) =>
            (FragmentId(s"${witness.value.name}.$id"), fragment.contramap[FieldType[K, H] :: T](_.head))
        }

        derivedInspectableT.fragments.mapValues(_.contramap[FieldType[K, H] :: T](_.tail)) ++ fragmentsR
      }
    }

  implicit val hnilDerivedInspectable: DerivedInspectable[HNil] = {
    new DerivedInspectable[HNil] {
      override val fragments: Map[FragmentId, inspection.Fragment[HNil]] =
        Map.empty[FragmentId, inspection.Fragment[HNil]]
    }
  }
}

trait LowPriorityDerivedInspectable1 extends LowPriorityDerivedInspectable0 {
  implicit def hconsDerivedInspectable1[K <: Symbol, H, Repr <: HList, T <: HList](
      implicit witness: Witness.Aux[K],
      gen: LabelledGeneric.Aux[H, Repr],
      derivedInspectableRepr: Lazy[DerivedInspectable[Repr]],
      derivedInspectableT: DerivedInspectable[T]
  ): DerivedInspectable[FieldType[K, H] :: T] =
    new DerivedInspectable[FieldType[K, H] :: T] {
      override val fragments: Map[FragmentId, inspection.Fragment[FieldType[K, H] :: T]] = {
        val fragmentsR = derivedInspectableRepr.value.fragments.map {
          case (FragmentId(id), fragment) =>
            (
              FragmentId(s"${witness.value.name}.$id"),
              fragment.contramap[FieldType[K, H] :: T](hcons => gen.to(hcons.head))
            )
        }

        derivedInspectableT.fragments.mapValues(_.contramap[FieldType[K, H] :: T](_.tail)) ++ fragmentsR
      }
    }
}

trait LowPriorityDerivedInspectable0 {
  implicit def hconsDerivedInspectable0[K <: Symbol, H, T <: HList](
      implicit witness: Witness.Aux[K],
      renderH: Lazy[Render[FieldType[K, H]]],
      derivedInspectableT: DerivedInspectable[T]
  ): DerivedInspectable[FieldType[K, H] :: T] =
    new DerivedInspectable[FieldType[K, H] :: T] {
      override val fragments: Map[FragmentId, inspection.Fragment[FieldType[K, H] :: T]] =
        derivedInspectableT.fragments.map {
          case (id, fragment) =>
            (id, fragment.contramap[FieldType[K, H] :: T](_.tail))
        } + (FragmentId(
          witness.value.name
        ) -> Fragment.getter(_.head)(renderH.value))
    }
}
