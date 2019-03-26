package akka.inspection

import akka.inspection.ActorInspection.{FinalizedFragment, RenderedFragment, UndefinedFragment}
import akka.inspection.util.Render
import cats.Contravariant

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
  final case class Const[S](fragment: String) extends Fragment[S] {
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
  final case class State[S](fragment: S => String) extends Fragment[S] {
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
  def apply[S, T: Render](t: T): Fragment[S] = Const(Render[T].render(t))

  /**
   * Build a [[Fragment]] that's evaluated at each use
   * and independent of the state [[S]].
   */
  def always[S, T: Render](t: => T): Fragment[S] = Always(() => Render[T].render(t))

  /**
   * Build a [[Fragment]] from the state [[S]].
   */
  def state[S, T: Render](t: S => T): Fragment[S] = State(t.andThen(Render[T].render))

  /**
   * Build a [[Fragment]] always returning `s`.
   */
  def fix[S](s: String): Fragment[S] = Const(s)

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
    def apply[T: Render](t: T): Fragment[S]      = Fragment(t)
    def always[T: Render](t: => T): Fragment[S]  = Fragment.always(t)
    def state[T: Render](t: S => T): Fragment[S] = Fragment.state(t)
    def sensitive: Fragment[S]                   = Fragment.sensitive
    def undefined: Fragment[S]                   = Fragment.undefined
  }

  implicit val fragmentContravariant: Contravariant[Fragment] = new Contravariant[Fragment] {
    override def contramap[A, B](fa: Fragment[A])(f: B => A): Fragment[B] = fa match {
      case Const(fragment)  => Const(fragment)
      case Always(fragment) => Always(fragment)
      case State(fragment)  => State(fragment.compose(f))
      case Undefined()      => Undefined()
    }
  }
}
