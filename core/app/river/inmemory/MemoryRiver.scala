package river.inmemory

import river.{NotificationStorage, EventLogger, FireEvent, River}
import river.handle.{Flow, Handler, Handlers}
import river.digest.{Digest, Digester, Digesters}
import river.data.Event
import akka.actor.{Props, ActorSystem}
import river.channel.Channel

/**
 * @author alari
 * @since 4/7/14
 */
object MemoryRiver extends River{
  override val storage: NotificationStorage = MemoryStorage

  override val digesters: Digesters = new Digesters {
    override def apply(v1: Option[String]): Digester = AnyDigester
  }

  override val handlers: Handlers = new Handlers {
    override def apply(v1: Event): Handler = AnyHandler
  }

  override val logger: EventLogger = MemoryLogger

  override lazy val fire: FireEvent = new FireEvent {
    lazy val system = ActorSystem("memory-river")

    lazy val digest = system.actorOf(Props[DigestActor], "digest")
    lazy val flow = {
      digest
      system.actorOf(Props[FlowActor], "flow")
    }

    override def apply(v1: Event): Unit = {
      flow ! Flow.Fired(v1)
      Thread.sleep(100)
    }
  }

  class DigestActor extends Digest {
    override def river: River = MemoryRiver

    import context.dispatcher
    import scala.concurrent.duration._
    context.system.scheduler.schedule(50 milliseconds, 185 milliseconds, self, Digest.CheckPending)
  }

  class FlowActor extends Flow {
    override def river: River = MemoryRiver
  }

  override def channels: Seq[Channel] = Seq(PlayLoggerChannel)
}
