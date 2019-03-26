package akka.inspection.laws

import akka.inspection.ActorInspection.FragmentId
import akka.inspection.grpc
import akka.inspection.manager._
import cats.Eq
import org.scalacheck.Arbitrary.{arbEither, arbString, arbitrary => getArbitrary}
import org.scalacheck.Cogen._
import org.scalacheck.Gen._
import org.scalacheck.{Arbitrary, Cogen, Gen}
import scalaz.Equal
import org.scalacheck.ScalacheckShapeless._

/**
 * Arbitrary instances for akka.inspection
 */
object arbitrary {
  implicit val arbGRPCFragmentIdsRequest: Arbitrary[grpc.FragmentIdsRequest] = Arbitrary(
    Gen.alphaNumStr.map(grpc.FragmentIdsRequest(_))
  )

  implicit val arbFragmentIdsRequest: Arbitrary[FragmentIdsRequest] = Arbitrary(
    Gen.alphaNumStr.map(FragmentIdsRequest(_))
  )

  implicit val arbGRPCActorNotInspectable: Arbitrary[grpc.Error.Error.ActorNotInspectable] = Arbitrary(
    getArbitrary[String].map(a => grpc.Error.Error.ActorNotInspectable(grpc.Error.ActorNotInspectable(a)))
  )

  implicit val arbGRPCFragmentIds: Arbitrary[grpc.FragmentIdsResponse.FragmentIds] = Arbitrary {
    for {
      state       <- getArbitrary[String]
      fragmentIds <- getArbitrary[Seq[String]]
    } yield grpc.FragmentIdsResponse.FragmentIds(state, fragmentIds)
  }

  implicit val arbGRPCFragmentIdsResponseFragmentIds: Arbitrary[grpc.FragmentIdsResponse.Res.FragmentIds] = Arbitrary {
    arbGRPCFragmentIds.arbitrary.map(grpc.FragmentIdsResponse.Res.FragmentIds)
  }

  implicit val arbGRPCUnreachableActor: Arbitrary[grpc.Error.Error.UnreachableInspectableActor] = Arbitrary(
    getArbitrary[String].map(
      a => grpc.Error.Error.UnreachableInspectableActor(grpc.Error.UnreachableInspectableActor(a))
    )
  )

  implicit val arbFragmentIdsResponseError: Arbitrary[grpc.FragmentIdsResponse.Res.Error] = Arbitrary(
    oneOf(arbGRPCActorNotInspectable.arbitrary, arbGRPCUnreachableActor.arbitrary)
      .map(a => grpc.FragmentIdsResponse.Res.Error(grpc.Error.of(a)))
  )

  implicit val arbGRPCFragmentIdsReponseEmpty: Arbitrary[grpc.FragmentIdsResponse.Res.Empty.type] = Arbitrary {
    const(grpc.FragmentIdsResponse.Res.Empty)
  }

  implicit val arbGRPCFragmentIdsResponse: Arbitrary[grpc.FragmentIdsResponse] = Arbitrary {
    oneOf(arbGRPCFragmentIdsResponseFragmentIds.arbitrary,
          arbFragmentIdsResponseError.arbitrary,
          arbGRPCFragmentIdsReponseEmpty.arbitrary).map(grpc.FragmentIdsResponse(_))
  }

  implicit val arbActorNotInspectable: Arbitrary[ActorNotInspectable] = Arbitrary(
    arbString.arbitrary.map(ActorNotInspectable)
  )

  implicit val arbFragmentId: Arbitrary[FragmentId] = Arbitrary(arbString.arbitrary.map(FragmentId))

  implicit val arbFragmentIdsReponse: Arbitrary[FragmentIdsResponse] =
    Arbitrary(arbEither[ActorNotInspectable, (String, List[FragmentId])].arbitrary.map(a => FragmentIdsResponse(a)))

  implicit val arbGRPCInspectableActorsRequest: Arbitrary[grpc.InspectableActorsRequest] = Arbitrary(
    Gen.const(grpc.InspectableActorsRequest())
  )

  implicit val arbInspectableActorsRequest: Arbitrary[InspectableActorsRequest.type] = Arbitrary(
    Gen.const(InspectableActorsRequest)
  )

  /* --- Cogen instances --- */
  implicit val cogenFragmentIdsRequest: Cogen[FragmentIdsRequest] = Cogen.cogenString.contramap(_.actor)

  implicit val cogenActorNotInspectable: Cogen[ActorNotInspectable] = cogenString.contramap(_.id)
  implicit val cogenFragmentId: Cogen[FragmentId]                   = cogenString.contramap(_.id)

  implicit val cogenFragmentIdsReponse: Cogen[FragmentIdsResponse] =
    cogenEither[Error, (String, List[FragmentId])].contramap(_.ids)

  implicit val cogenInspectableActorsRequest: Cogen[InspectableActorsRequest.type] =
    cogenUnit.contramap(_ => InspectableActorsRequest)

  /* --- Equals instances --- */
  implicit val eqGRPCFragmentIdsRequest: Equal[grpc.FragmentIdsRequest] =
    (a1: grpc.FragmentIdsRequest, a2: grpc.FragmentIdsRequest) => a1.actor == a2.actor

  implicit val eqFragmentIdsRequest: Equal[FragmentIdsRequest] = (a1: FragmentIdsRequest, a2: FragmentIdsRequest) =>
    a1.actor == a2.actor

  implicit val eqFragmentIdsResponse: Equal[FragmentIdsResponse] = (a1: FragmentIdsResponse, a2: FragmentIdsResponse) =>
    a1 == a2

  implicit val eqGRPCFragmentIdsResponse: Equal[grpc.FragmentIdsResponse] =
    (a1: grpc.FragmentIdsResponse, a2: grpc.FragmentIdsResponse) => a1 == a2

  implicit val eqInspectableActorsRequest: Equal[InspectableActorsRequest.type] =
    (_: InspectableActorsRequest.type, _: InspectableActorsRequest.type) => true

  implicit val eqGRPCInspectableActorsRequest: Equal[grpc.InspectableActorsRequest] =
    (_: grpc.InspectableActorsRequest, _: grpc.InspectableActorsRequest) => true

  implicit val eqResponseEvent: Eq[ResponseEvent] = Eq.fromUniversalEquals
}
