package akka.inspection

import akka.actor.{Actor, ActorContext, ActorPath, ActorRef}
import akka.inspection.ActorInspection._
import akka.inspection.ActorInspectorImpl.Group
import akka.pattern.pipe

import scala.concurrent.{ExecutionContext, Future}

trait ActorInspection { _: Actor =>
  def group: Group = Group.None
  def keys: Set[String]
  def query(key: String): QueryResult
  def queryAll: QueryResult

  private val akkaInspector = ActorInspector(context.system)

  override def aroundPreStart(): Unit = akkaInspector.put(self, keys, group)
  override def aroundPostStop(): Unit = akkaInspector.release(self)

  /**
    * Handles [[QueryRequest]] messages and runs the computations
    * on the [[ExecutionContext]] `runOn`.
    * @param runOn [[ExecutionContext]] on which to run.
    */
  def receiveQuery(runOn: ExecutionContext): Receive = {
    implicit val ec: ExecutionContext = runOn

    {
      // Match in two steps so you get the exhaustiveness check.
      case qr: QueryRequest =>
        qr match {
          case QueryRequest.Single(k) => Future(query(k)).pipeTo(sender())
          case QueryRequest.All       => Future(queryAll).pipeTo(sender())
        }
    }
  }

  def receiveChildren: Receive = {
    case rq: ActorInspection.ChildrenRequest.type => sender ! ChildrenResult(context.children.toList)
  }

  override protected def aroundReceive(receive: Receive, msg: Any): Unit =
    receiveQuery(context.system.dispatcher) orElse // TODO use a different EC
      receiveChildren orElse
      receive
}

private[inspection] object ActorInspection extends QueryMessages with ChildrenMessages

private[inspection] trait QueryMessages {

  /**
    * Messages to be sent to an actor implementing [[ActorInspection]]
    * to trigger a request.
    */
  private[inspection] sealed abstract class QueryRequest extends Product with Serializable

  private[inspection] object QueryRequest {

    /**
      * Query for the actor's state subset defined by the `key`.
      * @param key the key of the state element to query.
      */
    final case class Single(key: String) extends QueryRequest

    /**
      * Query for actor's entire state.
      */
    final case object All extends QueryRequest
  }

  /**
    * Messages sent by an actor implementing [[ActorInspection]]
    * to the actor that made the related request.
    */
  sealed abstract class QueryResult extends Product with Serializable
  object QueryResult {
    final case class Success(res: String)                  extends QueryResult
    final case class Failure(key: String, message: String) extends QueryResult
  }
}

trait ChildrenMessages {
  final case object ChildrenRequest
  final case class ChildrenResult(children: List[ActorRef])
}