package akka.inspection.inspectable.derivation

import akka.inspection.inspectable.Inspectable
import shapeless.Lazy

object semiauto {
  def deriveInspectable[A](implicit inspectable: Lazy[DerivedInspectable[A]]): Inspectable[A] = inspectable.value
}
