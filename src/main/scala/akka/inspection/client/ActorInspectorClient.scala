package akka.inspection.client

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.inspection.grpc
import akka.inspection.grpc.ActorInspectionServiceClient
import akka.inspection.service.Bla
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContextExecutor

object ActorInspectorClient {
  def main(args: Array[String]): Unit = {
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())

    implicit val sys: ActorSystem = ActorSystem("CLIENT", conf)
    implicit val mat: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = sys.dispatcher

    val clientSettings = GrpcClientSettings.connectToServiceAt("127.0.0.1", 8080).withTls(false)
    //GrpcClientSettings.fromConfig(grpc.ActorInspectionService.name)
    val client: grpc.ActorInspectionService = ActorInspectionServiceClient(clientSettings)

    Bla.bla(Array("--group", "bla"), client)
  }
}
