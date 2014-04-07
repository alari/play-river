package river

import river.handle.{Handler, Handlers}
import river.digest.{Digester, Digesters}
import river.inmemory.{MemoryLogger, MemoryStorage}
import river.data.Event
import river.channel.Channel


/**
 * @author alari
 * @since 4/7/14
 */
trait River {
  def fire: FireEvent

  def logger: EventLogger

  def handlers: Handlers

  def digesters: Digesters

  def storage: NotificationStorage

  def channels: Seq[Channel]

  def channel(id: String): Option[Channel] = channels.find(_.id == id)
}

object River extends River {

  import play.api.Play.current

  override def storage: NotificationStorage = current.plugin[NotificationStorage].getOrElse(MemoryStorage)

  override def digesters: Digesters = new Digesters {
    override def apply(v1: Option[String]): Digester = current.plugins.filter {
      case h: Digester if h.isDefinedFor(v1) => true
      case _ => false
    }.map {
      case d: Digester => d
    }.reduce[Digester] {
      case (h1: Digester, h2: Digester) => h1.compose(h2)
    }
  }

  override lazy val handlers: Handlers = new Handlers {
    override def apply(v1: Event): Handler = current.plugins.filter {
      case h: Handler if h.isDefinedFor(v1) => true
      case _ => false
    }.map {
      case h: Handler => h
    }.reduce[Handler] {
      case (h1: Handler, h2: Handler) => h1.compose(h2)
    }
  }

  override def logger: EventLogger = current.plugin[EventLogger].getOrElse(MemoryLogger)

  override def fire: FireEvent = current.plugin[FireEvent].getOrElse(FireEvent)

  override def channels: Seq[Channel] = current.plugins.filter {
    case c: Channel => true
    case _ => false
  }.map {
    case c: Channel => c
  }
}