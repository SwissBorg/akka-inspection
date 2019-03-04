package akka.inspection.inspectable

import akka.inspection
import akka.inspection.ActorInspection
import akka.inspection.ActorInspection.FragmentId
import akka.inspection.Fragment._
import cats.implicits._
import cats.{Always => _, _}
import shapeless.{::, Cached, Generic, HList, HNil, Lazy, Strict}

sealed trait DerivedInspectable[A] extends Inspectable[A]

object DerivedInspectable {
  def gen[A, R](implicit G: Generic.Aux[A, R], R: Cached[Strict[DerivedInspectable[R]]]): DerivedInspectable[A] =
    new DerivedInspectable[A] {
      override def fragments: Map[ActorInspection.FragmentId, inspection.Fragment[A]] =
        R.value.value.fragments.mapValues {
          case Fix(fragment)    => Fix(fragment)
          case Always(fragment) => Always(fragment)
          case Given(fragment)  => Given(fragment.compose(G.to))
          case Undefined()      => Undefined()
        }
    }

  implicit def hcons[H, T <: HList](implicit H: Lazy[Inspectable[H]],
                                    T: DerivedInspectable[T]): DerivedInspectable[H :: T] =
    new DerivedInspectable[H :: T] {
      override def fragments: Map[ActorInspection.FragmentId, inspection.Fragment[H :: T]] = {
        val fragmentsH = H.value.fragments
        val fragmentsT = T.fragments

        fragmentsH.foldLeft(Map.empty[FragmentId, inspection.Fragment[H :: T]]) {
          case (fragmentsHT, (id, fragmentH)) =>
            fragmentsHT + (id -> fragmentsT
              .get(id)
              .fold[inspection.Fragment[H :: T]](fragmentH.contramap(_.head))(
                fragmentT =>
                  ContravariantMonoidal[inspection.Fragment]
                    .product(fragmentH, fragmentT)
                    .contramap(hcons => (hcons.head, hcons.tail))
              ))
        }
      }
    }

  implicit val hnil: DerivedInspectable[HNil] = new DerivedInspectable[HNil] {
    override def fragments: Map[FragmentId, inspection.Fragment[HNil]] = Map.empty
  }

//  implicit def ccons[H, T <: Coproduct](implicit H: Lazy[Inspectable[H]],
//                                        T: DerivedInspectable[T]): DerivedInspectable[H :+: T] = ???
//    new DerivedInspectable[H :+: T] {
//      override def fragments: Map[FragmentId, inspection.Fragment[H :+: T]] = {
//        val fragmentsH = Inspectable[H].fragments
//        val fragmentsT = Inspectable[T].fragments
//
//        fragmentsH.foldLeft(Map.empty[FragmentId, inspection.Fragment[H :+: T]]) {
//          case (fragmentsHT, (id, fragmentH)) =>
//            val f: inspection.Fragment[H :+: T] = fragmentH
//            ???
//        }
//
//        ???
//      }
//    }

//  implicit val cnil: DerivedInspectable[CNil] = ???
//    override def fragments: Map[FragmentId, inspection.Fragment[CNil]] = Map.empty
//  }

}
