package akka.inspection.inspectable

import shapeless.{Cached, LabelledGeneric, Strict}

package object derivation {
  def gen[A, Repr](implicit gen: LabelledGeneric.Aux[A, Repr],
                   inspectableRepr: Cached[Strict[DerivedInspectable[Repr]]]): DerivedInspectable[A] =
    DerivedInspectable.gen[A, Repr]
}
