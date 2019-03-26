package akka.inspection

import akka.inspection.ActorInspection.{FinalizedFragment, RenderedFragment, UndefinedFragment}
import cats.Contravariant

/**
 * Describes how to extract a fragment from the state [[S]].
 *
 * @tparam S the type of state.
 */
sealed abstract private[inspection] class Fragment[-S] extends Product with Serializable {

  /**
   * Runs the [[Fragment]] to build a [[FinalizedFragment]].
   */
  def run(s: S): FinalizedFragment
}

private[inspection] object Fragment {

  /**
   * Describes a fragment whose value doesn't change.
   */
  final case class Const(fragment: String) extends Fragment[Any] {
    override def run(s: Any): FinalizedFragment = RenderedFragment(fragment)
  }

  /**
   * Describes a fragment whose value is evaluated at each access.
   */
  final case class Always(fragment: () => String) extends Fragment[Any] {
    override def run(s: Any): FinalizedFragment = RenderedFragment(fragment())
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
  final case class Undefined() extends Fragment[Any] {
    override def run(s: Any): FinalizedFragment = UndefinedFragment
  }

  /**
   * Build a [[Fragment]] always containing `t`.
   */
  def apply[T: Render](t: T): Fragment[Any] = Const(Render[T].render(t))

  /**
   * Build a [[Fragment]] that evaluates `t` at each access.
   */
  def always[T: Render](t: => T): Fragment[Any] = Always(() => Render[T].render(t))

  /**
   * Build a [[Fragment]] from the state [[S]].
   */
  def state[S, T: Render](t: S => T): Fragment[S] = State(t.andThen(Render[T].render))

  /**
   * Build a [[Fragment]] always returning `s`.
   */
  def fix(s: String): Fragment[Any] = Const(s)

  /**
   * Build a [[Fragment]] that hides sensitive data.
   */
  def sensitive[S]: Fragment[S] = fix("[SENSITIVE]")

  /**
   * [[Fragment]] fallback.
   */
  def undefined: Fragment[Any] = Undefined()

  /**
   * Helper to provide [[S]] so that the user that extends [[ActorInspection]]
   * does not have to annotate the state's type when building state fragments.
   */
  final class FragmentPartiallyApplied[S](val dummy: Boolean = true) extends AnyVal {
    def apply[T: Render](t: T): Fragment[Any]     = Fragment(t)
    def always[T: Render](t: => T): Fragment[Any] = Fragment.always(t)
    def state[T: Render](t: S => T): Fragment[S]  = Fragment.state(t)
    def sensitive: Fragment[Any]                  = Fragment.sensitive
    def undefined: Fragment[Any]                  = Fragment.undefined
  }

  implicit val fragmentContravariant: Contravariant[Fragment] = new Contravariant[Fragment] {
    override def contramap[A, B](fa: Fragment[A])(f: B => A): Fragment[B] = fa match {
      case State(fragment) => State(fragment.compose(f))
      case c: Const        => c
      case a: Always       => a
      case u: Undefined    => u
    }
  }
}
