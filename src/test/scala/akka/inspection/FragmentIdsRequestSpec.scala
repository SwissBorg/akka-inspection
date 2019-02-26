package akka.inspection
import akka.inspection.ActorInspectorManager.FragmentIdsRequest
import monocle.law.discipline
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalatest.FunSuite
import org.typelevel.discipline.scalatest.Discipline
import scalaz.Equal

class FragmentIdsRequestSpec extends FunSuite with Discipline {
  implicit val arbGRPCFragmentIdsRequest: Arbitrary[grpc.FragmentIdsRequest] = Arbitrary(
    Gen.alphaNumStr.map(grpc.FragmentIdsRequest(_))
  )

  implicit val arbFragmentIdsRequest: Arbitrary[FragmentIdsRequest] = Arbitrary(
    Gen.alphaNumStr.map(FragmentIdsRequest(_))
  )

  implicit val cogenFragmentIdsRequest: Cogen[FragmentIdsRequest] = Cogen.cogenString.contramap(_.path)

  implicit val eqGRPCFragmentIdsRequest: Equal[grpc.FragmentIdsRequest] =
    (a1: grpc.FragmentIdsRequest, a2: grpc.FragmentIdsRequest) => a1.actor == a2.actor

  implicit val eqFragmentIdsRequest: Equal[FragmentIdsRequest] = (a1: FragmentIdsRequest, a2: FragmentIdsRequest) =>
    a1.path == a2.path

  checkAll("FragmentIdsRequest", discipline.IsoTests(ActorInspectorManager.FragmentIdsRequest.grpcIso))
}
