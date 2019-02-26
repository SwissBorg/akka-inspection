package akka.inspection

import akka.inspection.laws.arbitrary._
import monocle.law.discipline
import org.scalatest.FunSuite
import org.typelevel.discipline.scalatest.Discipline

class FragmentIdsResponseSpec extends FunSuite with Discipline {
  checkAll("FragmentIdsResponse", discipline.PrismTests(ActorInspectorManager.FragmentIdsResponse.grpcPrism))
}
