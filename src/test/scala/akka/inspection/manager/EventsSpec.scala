package akka.inspection.manager

import akka.inspection.laws.arbitrary._
import monocle.law.discipline
import org.scalatest.FunSuite
import org.typelevel.discipline.scalatest.Discipline

class EventsSpec extends FunSuite with Discipline {
  checkAll("FragmentIdsRequest", discipline.IsoTests(FragmentIdsRequest.grpcIso))
  checkAll("InspectableActorsRequest", discipline.IsoTests(InspectableActorsRequest.grpcIso))

  checkAll("FragmentIdsResponse", discipline.PrismTests(FragmentIdsResponse.grpcPrism))
}
