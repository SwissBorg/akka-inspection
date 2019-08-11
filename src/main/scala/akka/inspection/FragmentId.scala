package akka.inspection

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
  def expand(expandIn: Set[FragmentId]): Set[FragmentId] =
    if (id === "*") {
      expandIn
    } else if (id.endsWith(".*") && !id.startsWith(".*")) {
      expandIn.filter {
        case FragmentId(id) => id.startsWith(this.id.init)
      }
    } else {
      expandIn.filter(_.id === id)
    }
}
