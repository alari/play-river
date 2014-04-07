package river

import akka.actor.{ActorSystem, Props}
import river.digest.Digest
import river.handle.Flow
import river.data.Event

/**
 * @author alari
 * @since 4/7/14
 */
trait AkkaFireEvent extends FireEvent{
  def system: ActorSystem

  lazy val digest = system.actorOf(Props[Digest], "digest")

  lazy val flow = {
    import scala.concurrent.duration._
    import play.api.Play.current
    system.scheduler.schedule(
      current.configuration.getMilliseconds("river.digest.delay").map(_ millis).getOrElse(10 seconds),
      current.configuration.getMilliseconds("river.digest.period").map(_ millis).getOrElse(600 seconds),

      digest, Digest.CheckPending)(system.dispatcher, system.deadLetters)

    system.actorOf(Props[Flow], "flow")
  }

  override def apply(v1: Event): Unit = {
    flow ! Flow.Fired(v1)
  }
}
