package akka.inspection.server

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.UseHttp2.Always
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.inspection.{ActorInspector, ActorInspectorImpl, grpc}
import akka.inspection.service.ActorInspectionServiceImpl
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}

class ActorInspectorServer(system: ActorSystem) {
  implicit val sys: ActorSystem = system
  implicit val mat: Materializer = ActorMaterializer()
  implicit val ec: ExecutionContext = sys.dispatcher

  def run(): Future[Http.ServerBinding] = {
    val service: HttpRequest => Future[HttpResponse] =
      grpc.ActorInspectionServiceHandler(new ActorInspectionServiceImpl(ActorInspector(system)))

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

object ActorInspectorServer {
  def main(args: Array[String]): Unit = {
    // Important: enable HTTP/2 in ActorSystem's config
    // We do it here programmatically, but you can also set it in the application.conf
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())

    val system = ActorSystem("HELLOWORLD", conf)

    Cluster(system).joinSeedNodes(List(Cluster(system).selfAddress))

    new ActorInspectorServer(system).run()
    // ActorSystem threads will keep the app alive until `system.terminate()` is called
  }
}
