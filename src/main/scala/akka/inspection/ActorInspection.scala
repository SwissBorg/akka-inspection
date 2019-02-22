package akka.inspection

import akka.actor.{Actor, ActorRef}
import akka.inspection.ActorInspection._
import akka.inspection.ActorInspectorManager.Keys.Key
import akka.pattern.pipe
import cats.Show

import scala.concurrent.{ExecutionContext, Future}

trait ActorInspection { _: Actor =>
  type Group = akka.inspection.ActorInspectorManager.Groups.Group
  type Key   = akka.inspection.ActorInspectorManager.Keys.Key

  def responses: Map[Key, QueryResponse]
  def groups: Set[Group] = Set.empty

  private val akkaInspector = ActorInspector(context.system)

  override def aroundPreStart(): Unit = akkaInspector.put(self, responses.keySet, groups)
  override def aroundPostStop(): Unit = akkaInspector.release(self)

  private def query(k: Key): QueryResponse = responses.getOrElse(k, QueryResponse.custom("bb")) match {
    case QueryResponse.LazySuccess(m) => QueryResponse.Success(m())
    case r                            => r
  }

  private def queryAll: QueryResponse = ???

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
          case QueryRequest.One(k) => Future(query(k)).pipeTo(sender())
          case QueryRequest.All    => Future(queryAll).pipeTo(sender())
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
    final case class One(key: Key) extends QueryRequest

    /**
      * Query for actor's entire state.
      */
    final case object All extends QueryRequest
  }

  /**
    * Messages sent by an actor implementing [[ActorInspection]]
    * to the actor that made the related request.
    */
  sealed abstract class QueryResponse extends Product with Serializable
  object QueryResponse {
    final case class LazySuccess(res: () => String)        extends QueryResponse
    final case class Success(res: String)                  extends QueryResponse
    final case class Failure(key: String, message: String) extends QueryResponse

    def apply[T: Show](t: T): QueryResponse                = Success(Show[T].show(t))
    def later[T: Show](t: => T): QueryResponse             = LazySuccess(() => Show[T].show(t))
    def custom(s: String): QueryResponse                   = Success(s)
    def error(key: String, message: String): QueryResponse = Failure(key, message)
  }

  sealed abstract class BackPressureEvent extends Product with Serializable
  final case object Init                  extends BackPressureEvent
  final case object Ack                   extends BackPressureEvent
  final case object Complete              extends BackPressureEvent
}

trait ChildrenMessages {
  final case object ChildrenRequest
  final case class ChildrenResult(children: List[ActorRef])
}
