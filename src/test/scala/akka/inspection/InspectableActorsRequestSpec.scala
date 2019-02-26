package akka.inspection

import monocle.law.discipline
import org.scalatest.FunSuite
import org.typelevel.discipline.scalatest.Discipline
import akka.inspection.laws.arbitrary._

class InspectableActorsRequestSpec extends FunSuite with Discipline {
  checkAll("InspectableActorsRequest", discipline.IsoTests(ActorInspectorManager.InspectableActorsRequest.grpcIso))
}
