package com.swissborg.akkainspection.extension

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, Http2, HttpConnectionContext}
import com.swissborg.akkainspection.grpc
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Server exposing the actor inspection service to the outside of the cluster.
  *
  * @param inspectionService an instance of the service.
  * @param system the actor system.
  * @param interface the interface on which to listen.
  * @param port the port on which to listen.
  */
private[extension] class ActorInspectorServer(
    inspectionService: ActorInspectorImpl,
    system: ActorSystem,
    interface: String,
    port: Int
) extends StrictLogging {
  implicit val sys: ActorSystem     = system
  implicit val mat: Materializer    = ActorMaterializer()
  implicit val ec: ExecutionContext = sys.dispatcher

  def run(): Future[Http.ServerBinding] = {
    val service: HttpRequest => Future[HttpResponse] =
      grpc.ActorInspectionServiceHandler(inspectionService)

    val bound: Future[Http.ServerBinding] =
      Http2().bindAndHandleAsync(service, interface = interface, port = port, HttpConnectionContext())

    bound.onComplete {
      case Failure(exception) =>
        logger.error("Failed to start the inspector-server!", exception)
      case Success(binding) =>
        logger.info(s"gRPC server bound to: ${binding.localAddress}")
    }

    bound
  }
}
