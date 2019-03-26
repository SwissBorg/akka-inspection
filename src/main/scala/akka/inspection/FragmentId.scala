package akka.inspection

import akka.inspection.inspectable.Inspectable
import cats.implicits._

/**
 * Represents the identifier of a subset of an actor's state.
 *
 * @see [[Fragment]]
 */
final case class FragmentId(id: String) extends AnyVal {

  /**
   * Returns the expanded fragment-ids.
   *
   * Rules:
   *   - "*" expands to all the inspectable fragments
   *   - "a.b.*" expands to all the child fragments of "a.b"l
   *   - otherwise expands to itself
   */
  def expand[S](implicit inspectableS: Inspectable[S]): Set[FragmentId] =
    if (id.endsWith(".*") && !id.startsWith(".*")) {
      inspectableS.fragments.keySet.filter {
        case FragmentId(id) => id.startsWith(this.id.init)
      }
    } else if (id === "*") {
      inspectableS.fragments.keySet
    } else {
      inspectableS.fragments.keySet.filter(_.id === id)
    }
}
