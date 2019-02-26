package akka.inspection
import akka.inspection.ActorInspection.FragmentId
import akka.inspection.ActorInspectorManager.{ActorNotInspectable, FragmentIdsRequest, FragmentIdsResponse}
import monocle.law.discipline
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import org.scalacheck.Cogen._
import org.scalatest.FunSuite
import org.typelevel.discipline.scalatest.Discipline
import scalaz.Equal

class FragmentIdsResponseSpec extends FunSuite with Discipline {
  val arbGRPCActorNotInspectable: Arbitrary[grpc.Error.ActorNotInspectable] = Arbitrary(
    arbitrary[String].map(a => grpc.Error.ActorNotInspectable(a))
  )

  val arbGRPCFragmentIds: Arbitrary[grpc.FragmentIdsResponse.FragmentIds] = Arbitrary {
    arbitrary[Seq[String]].map(grpc.FragmentIdsResponse.FragmentIds(_))
  }

  implicit val arbGRPCFragmentIdsResponse: Arbitrary[grpc.FragmentIdsResponse] = Arbitrary {
    val fragmentIds: Arbitrary[grpc.FragmentIdsResponse.Res.FragmentIds] = Arbitrary {
      arbGRPCFragmentIds.arbitrary.map(grpc.FragmentIdsResponse.Res.FragmentIds)
    }

    val error: Arbitrary[grpc.FragmentIdsResponse.Res.Error] = Arbitrary {
      arbGRPCActorNotInspectable.arbitrary.map(grpc.FragmentIdsResponse.Res.Error)
    }

    val empty: Arbitrary[grpc.FragmentIdsResponse.Res.Empty.type] = Arbitrary {
      const(grpc.FragmentIdsResponse.Res.Empty)
    }

    oneOf(fragmentIds.arbitrary, error.arbitrary, empty.arbitrary).map(grpc.FragmentIdsResponse(_))
  }

  implicit val arbFragmentIdsReponse: Arbitrary[FragmentIdsResponse] = {
    implicit val arbActorNotInspectable: Arbitrary[ActorNotInspectable] = Arbitrary(
      arbString.arbitrary.map(ActorNotInspectable)
    )

    implicit val arbFragmentId: Arbitrary[FragmentId] = Arbitrary(arbString.arbitrary.map(FragmentId))

    Arbitrary(arbEither[ActorNotInspectable, List[FragmentId]].arbitrary.map(a => FragmentIdsResponse(a)))
  }

  implicit val cogenActorNotInspectable: Cogen[ActorNotInspectable] = cogenString.contramap(_.id)
  implicit val cogenFragmentId: Cogen[FragmentId] = cogenString.contramap(_.id)

  implicit val cogenFragmentIdsReponse: Cogen[FragmentIdsResponse] =
    cogenEither[ActorNotInspectable, List[FragmentId]].contramap(_.keys)

  implicit val eqFragmentIdsResponse: Equal[FragmentIdsResponse] = (a1: FragmentIdsResponse, a2: FragmentIdsResponse) =>
    a1 == a2
  implicit val eqGRPCFragmentIdsResponse: Equal[grpc.FragmentIdsResponse] =
    (a1: grpc.FragmentIdsResponse, a2: grpc.FragmentIdsResponse) => a1 == a2

  checkAll("FragmentIdsResponse", discipline.PrismTests(ActorInspectorManager.FragmentIdsResponse.grpcPrism))
}
