package akka.inspection.manager

import akka.inspection.laws.arbitrary._
import monocle.law.discipline
import org.scalatest.FunSuite
import org.typelevel.discipline.scalatest.Discipline
import akka.inspection.manager._


class InspectableActorsRequestSpec extends FunSuite with Discipline {
  checkAll("InspectableActorsRequest", discipline.IsoTests(InspectableActorsRequest.grpcIso))
}
