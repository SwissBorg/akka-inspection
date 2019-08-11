package akka.inspection.inspectable.derivation

import shapeless.{Cached, LabelledGeneric, Strict}

object auto {
  implicit def autoGen[A, Repr](implicit gen: LabelledGeneric.Aux[A, Repr],
                                inspectableRepr: Cached[Strict[DerivedInspectable[Repr]]]): DerivedInspectable[A] =
    DerivedInspectable.gen[A, Repr]
}
