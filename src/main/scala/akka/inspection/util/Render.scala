package akka.inspection.util

import akka.protobuf.GeneratedMessage

trait Render[T] {
  def render(t: T): String
}

object Render {
  def apply[T](implicit ev: Render[T]): Render[T] = ev

  implicit def toStringRender[T]: Render[T] = (t: T) => t.toString
}
