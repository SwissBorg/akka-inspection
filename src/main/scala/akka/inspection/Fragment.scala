package akka.inspection

import akka.inspection.ActorInspection.{FinalizedFragment, RenderedFragment, UndefinedFragment}
import akka.inspection.util.Render
import cats.ContravariantMonoidal

/**
 * Describes how to extract a fragment from the state [[S]].
 *
 * @tparam S the type of state.
 */
sealed abstract private[inspection] class Fragment[S] extends Product with Serializable {

  /**
   * Runs the [[Fragment]] to build a [[FinalizedFragment]].
   */
  def run(s: S): FinalizedFragment
}

private[inspection] object Fragment {

  /**
   * Describes a fragment whose value doesn't change.
   */
  final case class Fix[S](fragment: String) extends Fragment[S] {
    override def run(s: S): FinalizedFragment = RenderedFragment(fragment)
  }

  /**
   * Describes a fragment whose value is evaluated at each access.
   */
  final case class Always[S](fragment: () => String) extends Fragment[S] {
    override def run(s: S): FinalizedFragment = RenderedFragment(fragment())
  }

  /**
   * Describes a fragment dependent on [[S]].
   */
  final case class Given[S](fragment: S => String) extends Fragment[S] {
    def run(s: S): FinalizedFragment = RenderedFragment(fragment(s))
  }

  /**
   * Describes an undefined fragment.
   */
  final case class Undefined[S]() extends Fragment[S] {
    override def run(s: S): FinalizedFragment = UndefinedFragment
  }

  /**
   * Build a [[Fragment]] independent of the state [[S]].
   */
  def apply[S, T: Render](t: T): Fragment[S] = Fix(Render[T].render(t))

  /**
   * Build a [[Fragment]] that's evaluated at each use
   * and independent of the state [[S]].
   */
  def always[S, T: Render](t: => T): Fragment[S] = Always(() => Render[T].render(t))

  /**
   * Build a [[Fragment]] from the state [[S]].
   */
  def state[S, T: Render](t: S => T): Fragment[S] = Given(t.andThen(Render[T].render))

  /**
   * Build a [[Fragment]] always returning `s`.
   */
  def fix[S](s: String): Fragment[S] = Fix(s)

  /**
   * Build a [[Fragment]] that hides sensitive data.
   */
  def sensitive[S]: Fragment[S] = fix("[SENSITIVE]")

  /**
   * [[Fragment]] fallback.
   */
  def undefined[S]: Fragment[S] = Undefined()

  /**
   * Helper to provide [[S]] so that the user that extends [[ActorInspection]]
   * does not have to annotate the state's type when building state fragments.
   */
  final class FragmentPartiallyApplied[S](val dummy: Boolean = true) extends AnyVal {
    def apply[T: Render](t: T): Fragment[S] = Fragment(t)
    def always[T: Render](t: => T): Fragment[S] = Fragment.always(t)
    def state[T: Render](t: S => T): Fragment[S] = Fragment.state(t)
    def fix(s: String): Fragment[S] = Fragment.fix(s)
    def sensitive: Fragment[S] = Fragment.sensitive
    def undefined: Fragment[S] = Fragment.undefined
  }

  implicit val fragmentContravariantMonoidal: ContravariantMonoidal[Fragment] = new ContravariantMonoidal[Fragment] {
    override def contramap[A, B](fa: Fragment[A])(f: B => A): Fragment[B] = fa match {
      case Fix(fragment)    => Fix(fragment)
      case Always(fragment) => Always(fragment)
      case Given(fragment)  => Given(fragment.compose(f))
      case Undefined()      => Undefined()
    }

    override def unit: Fragment[Unit] = Undefined()

    override def product[A, B](fa: Fragment[A], fb: Fragment[B]): Fragment[(A, B)] = (fa, fb) match {
      case (Fix(fragment1), Fix(fragment2))    => Fix(s"($fragment1, $fragment2)")
      case (Fix(fragment1), Always(fragment2)) => Always(() => s"($fragment1, $fragment2)")
      case (Fix(fragment), Given(fragmentB))   => Given { case (_, b) => s"($fragment, ${fragmentB(b)})" }
      case (Fix(fragment), Undefined())        => Fix(s"($fragment, )")

      case (Always(fragment1), Always(fragment2)) => Always(() => s"($fragment1, $fragment2)")
      case (Always(fragment1), Fix(fragment2))    => Always(() => s"($fragment1, $fragment2)")
      case (Always(fragment1), Given(fragmentB))  => Given { case (_, b) => s"($fragment1, ${fragmentB(b)})" }
      case (Always(fragment1), Undefined())       => Always(() => s"($fragment1, )")

      case (Given(fragmentA), Given(fragmentB)) => Given { case (a, b) => s"(${fragmentA(a)}, ${fragmentB(b)})" }
      case (Given(fragmentA), Fix(fragment))    => Given { case (a, _) => s"(${fragmentA(a)}, $fragment)" }
      case (Given(fragmentA), Always(fragment)) => Given { case (a, _) => s"(${fragmentA(a)}, $fragment)" }
      case (Given(fragmentA), Undefined())      => Given { case (a, _) => s"(${fragmentA(a)}, )" }

      case (Undefined(), Fix(fragment))    => Fix(s"(, $fragment)")
      case (Undefined(), Always(fragment)) => Always(() => s"(, $fragment)")
      case (Undefined(), Given(fragmentB)) => Given { case (_, b) => s"(, ${fragmentB(b)})" }
      case (Undefined(), Undefined())      => Undefined()
    }
  }
}
