package akka.inspection.client

import akka.actor.ActorSystem
import akka.inspection.client.InteractiveMenus.MainMenu
import akka.inspection.grpc.ActorInspectionServiceClient
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

    MainMenu(ActorInspectionServiceClient(ClientConfig.parse(args, conf)), scala.io.Source.stdin.getLines())

//    Service.execute(Array("--group", "hello"), client)
////    Service.execute(Array("--groups", "akka://HELLOWORLD/user/$a"), client)
////    Service.execute(Array("--fragment-ids", "akka://HELLOWORLD/user/$b"), client)
//    Service.execute(Array("--inspectable"), client)
//    Service.execute(Array("--fragment-ids", "akka://HELLOWORLD/user/stateless-actor-2"), client)
//    Service.execute(Array("--fragments", "akka://HELLOWORLD/user/stateless-actor-2", "a.b.*"), client)
  }

}
