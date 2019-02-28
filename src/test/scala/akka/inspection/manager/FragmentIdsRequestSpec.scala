package akka.inspection.manager

import akka.inspection.laws.arbitrary._
import monocle.law.discipline
import org.scalatest.FunSuite
import org.typelevel.discipline.scalatest.Discipline
import akka.inspection.manager._

class FragmentIdsRequestSpec extends FunSuite with Discipline {
  checkAll("FragmentIdsRequest", discipline.IsoTests(FragmentIdsRequest.grpcIso))
}
