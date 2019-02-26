package akka.inspection.util
import akka.inspection.grpc
import monocle.Prism

object Lenses {
//  val successfulStateFragmentResponse: Prism[grpc.StateFragmentsResponse, Map[String, String]] =
//    Prism.partial[grpc.StateFragmentsResponse, Map[String, String]] {
//      case grpc.StateFragmentsResponse(
//          grpc.StateFragmentsResponse.FragmentsRes.Success(grpc.StateFragmentsResponse.Fragments(fs))
//          ) =>
//        fs
//    }(
//      fs =>
//        grpc.StateFragmentsResponse(
//          grpc.StateFragmentsResponse.FragmentsRes.Success(grpc.StateFragmentsResponse.Fragments(fs))
//      )
//    )
//
//  val failedStateFragmentResponse: Prism[grpc.StateFragmentsResponse, String] =
//    Prism.partial[grpc.StateFragmentsResponse, String] {
//      case grpc.StateFragmentsResponse(grpc.StateFragmentsResponse.FragmentsRes.Failure(err)) => err
//    }(
//      err =>
//        grpc.StateFragmentsResponse(
//          grpc.StateFragmentsResponse.FragmentsRes.Failure(err)
//      )
//    )
//
//  val successfulActorGroupsResponse: Prism[grpc.ActorGroupsResponse, Seq[String]] =
//    Prism.partial[grpc.ActorGroupsResponse, Seq[String]] {
//      case grpc.ActorGroupsResponse(
//          grpc.ActorGroupsResponse.GroupsRes.Success(grpc.ActorGroupsResponse.Groups(groups))
//          ) =>
//        groups
//    }(
//      groups =>
//        grpc.ActorGroupsResponse(
//          grpc.ActorGroupsResponse.GroupsRes.Success(grpc.ActorGroupsResponse.Groups(groups))
//      )
//    )
//
//  val failedActorGroupsResponse: Prism[grpc.ActorGroupsResponse, String] =
//    Prism.partial[grpc.ActorGroupsResponse, String] {
//      case grpc.ActorGroupsResponse(grpc.ActorGroupsResponse.GroupsRes.Failure(err)) => err
//    }(err => grpc.ActorGroupsResponse(grpc.ActorGroupsResponse.GroupsRes.Failure(err)))
}
