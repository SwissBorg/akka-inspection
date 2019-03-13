package akka.inspection.client

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.inspection.grpc
import akka.inspection.grpc.ActorInspectionServiceClient
import akka.inspection.service.Service
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContextExecutor

object ActorInspectorClient {
  def main(args: Array[String]): Unit = {
    val conf = ConfigFactory.load()
//      .parseString("akka.http.server.preview.enable-http2 = on")
//      .withFallback(ConfigFactory.defaultApplication())

    implicit val sys: ActorSystem             = ActorSystem("CLIENT", conf)
    implicit val mat: ActorMaterializer       = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = sys.dispatcher

    val clientSettings = GrpcClientSettings
      .connectToServiceAt(conf.getString("akka.inspection.server.hostname"), conf.getInt("akka.inspection.server.port"))
      .withTls(false)

    val client: grpc.ActorInspectionService = ActorInspectionServiceClient(clientSettings)

    println("therre")
    for (line <- scala.io.Source.stdin.getLines()) {
      println("here")
      val splits = split(line)
      println(splits)
      Service.execute(splits, client)
    }

//    Service.execute(Array("--group", "hello"), client)
////    Service.execute(Array("--groups", "akka://HELLOWORLD/user/$a"), client)
////    Service.execute(Array("--fragment-ids", "akka://HELLOWORLD/user/$b"), client)
//    Service.execute(Array("--inspectable"), client)
//    println("")
//    Service.execute(Array("--fragment-ids", "akka://HELLOWORLD/user/stateless-actor-2"), client)
//    println("")
//    Service.execute(Array("--fragments", "akka://HELLOWORLD/user/stateless-actor-2", "a.b.*"), client)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  def split(s: String): Array[String] = {
    println("bla")
    val p = "\"([^\"]*)\"|[^\"\\s]+".r

    val res = p
      .findAllMatchIn(s)
      .map(
        m => if (m.group(1) != null) m.group(1) else m.group(0)
      )
      .toArray
    println(s"sad: ${res.toList}")

    res
  }
}
