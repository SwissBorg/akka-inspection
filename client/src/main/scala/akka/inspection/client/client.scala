package akka.inspection

package object client {

  /**
   * Clears stdin.
   */
  def clearScreen(): Unit = print("\u001b[2J")

  /**
   * Run `f` on the input and advance it by one step.
   */
  def read(input: Iterator[String])(f: String => Unit): Unit =
    if (input.hasNext) {
      f(input.next())
    } else {
      println("NO INPUT")
    }
}