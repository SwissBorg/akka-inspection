package akka.inspection.manager

import akka.inspection.laws.arbitrary._
import monocle.law.discipline
import org.scalatest.FunSuite
import org.typelevel.discipline.scalatest.Discipline

class InspectableActorsRequestSpec extends FunSuite with Discipline {
  checkAll("InspectableActorsRequest", discipline.IsoTests(ActorInspectorManager.InspectableActorsRequest.grpcIso))
}
