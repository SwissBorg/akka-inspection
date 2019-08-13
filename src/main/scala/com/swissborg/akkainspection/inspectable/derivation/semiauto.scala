package com.swissborg.akkainspection.inspectable.derivation

import com.swissborg.akkainspection.inspectable.Inspectable
import shapeless.Lazy

object semiauto {

  def deriveInspectable[A](implicit inspectable: Lazy[DerivedInspectable[A]]): Inspectable[A] =
    inspectable.value
}
