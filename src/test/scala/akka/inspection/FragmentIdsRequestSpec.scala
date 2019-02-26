package akka.inspection

import akka.inspection.laws.arbitrary._
import monocle.law.discipline
import org.scalatest.FunSuite
import org.typelevel.discipline.scalatest.Discipline

class FragmentIdsRequestSpec extends FunSuite with Discipline {
  checkAll("FragmentIdsRequest", discipline.IsoTests(ActorInspectorManager.FragmentIdsRequest.grpcIso))
}
