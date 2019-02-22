package akka.inspection

import akka.actor.{Actor, ActorRef}
import akka.inspection
import akka.inspection.ActorInspection._
import akka.inspection.ActorInspectorManager.Keys.Key
import akka.pattern.pipe
import cats.Show

import scala.concurrent.{ExecutionContext, Future}

trait ActorInspection[S] { _: Actor =>
  type Group         = akka.inspection.ActorInspectorManager.Groups.Group
  type Key           = akka.inspection.ActorInspectorManager.Keys.Key
  type QueryResponse = akka.inspection.ActorInspection.QueryResponse
  val QueryResponse: inspection.ActorInspection.QueryResponse.type = akka.inspection.ActorInspection.QueryResponse

  implicit def showS: Show[S]

  def responses(s: S): Map[Key, QueryResponse]
  def groups: Set[Group] = Set.empty
  def fallBack(k: Key): QueryResponse =
    QueryResponse.error(k, s"No 'QueryResponse' defined for the key '${k.value}'.")

  private def allResponse(s: S): Map[Key, QueryResponse] = responses(s) + (Key("all") -> QueryResponse.now(s))

  def inspectableReceive(s: S)(r: Receive): Receive = inspectionReceive(s) orElse r

  protected def inspectionReceive(s: S): Receive = {
    case r: QueryRequest => handleQuery(s, r)(context.system.getDispatcher)
  }

  protected def query(s: S, k: Key): QueryResponse =
    allResponse(s).getOrElse(k, fallBack(k)) match {
      case QueryResponse.LazySuccess(m) => m()
      case r                            => r
    }

  private def queryAll(s: S): QueryResponse = ???

  protected def handleQuery(s: S, q: QueryRequest)(runOn: ExecutionContext): Unit = {
    implicit val ec: ExecutionContext = runOn

    q match {
      case QueryRequest.One(k) =>
        Future(query(s, k)).pipeTo(sender())
      case QueryRequest.All => Future(queryAll(s)).pipeTo(sender())
    }
  }
}

private[inspection] object ActorInspection {

  /**
    * Messages to be sent to an actor implementing [[ActorInspection]]
    * to trigger a request.
    */
  sealed abstract class QueryRequest extends Product with Serializable

  object QueryRequest {

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
    final case class Success(res: String)               extends QueryResponse
    final case class LazySuccess(res: () => Success)    extends QueryResponse
    final case class Failure(key: Key, message: String) extends QueryResponse

    def now[T: Show](t: T): Success               = Success(Show[T].show(t))
    def later[T: Show](t: => T): LazySuccess      = LazySuccess(() => now(t))
    def custom(s: String): Success                = Success(s)
    def error(key: Key, message: String): Failure = Failure(key, message)
  }

  sealed abstract class BackPressureEvent extends Product with Serializable
  final case object Init                  extends BackPressureEvent
  final case object Ack                   extends BackPressureEvent
  final case object Complete              extends BackPressureEvent

  final case object ChildrenRequest
  final case class ChildrenResult(children: List[ActorRef])
}
