package akka.inspection

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.inspection
import akka.inspection.ActorInspection._
import akka.inspection.ActorInspectorManager.Keys.Key
import akka.pattern.pipe
import cats.Show

import scala.concurrent.{ExecutionContext, Future}

trait ActorInspection extends Actor { _: Actor =>
  type Group         = akka.inspection.ActorInspectorManager.Groups.Group
  type Key           = akka.inspection.ActorInspectorManager.Keys.Key
  type QueryResponse = akka.inspection.ActorInspection.QueryResponse
  val QueryResponse: inspection.ActorInspection.QueryResponse.type = akka.inspection.ActorInspection.QueryResponse

  def responses: Map[Key, QueryResponse]
  def groups: Set[Group] = Set.empty

//  private val akkaInspector = ActorInspector(context.system)

  override protected[akka] def aroundPreStart(): Unit = () //akkaInspector.put(self, responses.keySet, groups)
  override protected[akka] def aroundPostStop(): Unit = () //akkaInspector.release(self)

  private def query(k: Key): QueryResponse =
    responses.getOrElse(k, QueryResponse.error(k, s"No 'QueryResponse' defined for the key ${k.value}.")) match {
      case QueryResponse.LazySuccess(m) => val a = m(); a
      case r                            => println(r); r
    }

  private def queryAll: QueryResponse = ???

  /**
    * Handles [[QueryRequest]] messages and runs the computations
    * on the [[ExecutionContext]] `runOn`.
    *
    * @param runOn [[ExecutionContext]] on which to run.
    */
  def handleQuery(runOn: ExecutionContext)(msg: Any): Unit = {
    implicit val ec: ExecutionContext = runOn

    msg match {
      // Match in two steps so you get the exhaustiveness check.
      case qr: QueryRequest =>
        qr match {
          case QueryRequest.One(k) =>
            Future(query(k)).pipeTo(sender())
          case QueryRequest.All => Future(queryAll).pipeTo(sender())
        }
      case _ => println("HHEEEREE")
    }
  }

  def receiveChildren: Receive = {
    case rq: ActorInspection.ChildrenRequest.type =>
      sender ! ChildrenResult(context.children.toList)
  }

  override protected[akka] def aroundReceive(receive: Receive, msg: Any): Unit = {
    handleQuery(context.system.dispatcher)(msg)
    super.aroundReceive(receive, msg)
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

    def now[T: Show](t: T): Success               = Success(t.toString)
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
