package akka.inspection.util

trait Render[T] {
  def render(t: T): String
}

object Render {
  def apply[T](implicit ev: Render[T]): Render[T] = ev
}
