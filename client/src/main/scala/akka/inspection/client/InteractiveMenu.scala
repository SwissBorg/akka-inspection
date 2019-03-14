package akka.inspection.client

import akka.inspection.grpc

abstract class InteractiveMenu extends ((grpc.ActorInspectionService, Iterator[String]) => Unit) {
  override def apply(client: grpc.ActorInspectionService, input: Iterator[String]): Unit
}
