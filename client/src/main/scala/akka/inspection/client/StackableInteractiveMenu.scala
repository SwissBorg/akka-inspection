package akka.inspection.client

abstract class StackableInteractiveMenu extends InteractiveMenu {
  val previous: InteractiveMenu
}