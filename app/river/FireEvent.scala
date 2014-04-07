package river

import river.data.Event
import akka.actor.ActorSystem

/**
 * @author alari
 * @since 4/7/14
 */
trait FireEvent extends (Event => Unit)

object FireEvent extends AkkaFireEvent {
  lazy val system = ActorSystem("memory-river")
}
