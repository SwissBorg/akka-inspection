package akka.inspection.util
import akka.actor.ActorRef

object ActorRefUtil {
  def asString(ref: ActorRef): String = ref.path.address.toString
}
