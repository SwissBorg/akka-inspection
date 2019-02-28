package akka.inspection.server

import akka.actor.ActorSystem
import akka.http.scaladsl.UseHttp2.Always
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.inspection.{grpc, ActorInspectorImpl}
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.{ExecutionContext, Future}

class ActorInspectorServer(inspectionService: ActorInspectorImpl, system: ActorSystem) {
  implicit val sys: ActorSystem = system
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = sys.dispatcher

  def run(): Future[Http.ServerBinding] = {
    val service: HttpRequest => Future[HttpResponse] = grpc.ActorInspectionServiceHandler(inspectionService)

    val bound: Future[Http.ServerBinding] = Http().bindAndHandleAsync(service,
                                                                      interface = "127.0.0.1",
                                                                      port = 8080,
                                                                      connectionContext =
                                                                        HttpConnectionContext(http2 = Always))

    bound.foreach { binding =>
      println(s"gRPC server bound to: ${binding.localAddress}")
    }

    bound
  }
}
