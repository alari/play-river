package river.handle

import akka.actor.Actor
import river.data.{Notification, Event}
import scala.concurrent.Future
import org.joda.time.DateTime
import river.River
import river.channel.Channel

/**
 * @author alari
 * @since 4/7/14
 */
class Flow extends Actor {

  import context.dispatcher
  import Flow._

  def river: River = River

  override def receive: Receive = {
    case Fired(e) =>
      river.logger.store(e).map(es => self ! Stored(es))

    case Stored(e) =>
      river.handlers(e).actions(e).map(_.foreach(_(river.storage).map {
        case n: Notification =>
          self ! Notify(e, n)
      }))

    case Notify(e, n) =>

      val handler = river.handlers(e)

      val deliveredInstantly = (Future sequence handler.channels.instant.map(c =>
        handler.instantView(c, (e, n)).flatMap {
          v =>
            c.asInstanceOf[Channel.Instant[c.InstantVM]].instant(v.asInstanceOf[c.InstantVM])
        }
      )).map(_.exists(b => b))

      val digest = handler.channels.digest.map {
        case (c, delay) =>
          (c, DateTime.now().plusMinutes(delay.toMinutes.toInt))
      }

      val digestIfNotInstantly = handler.channels.digestIfNotInstantly.map {
        case (c, delay) =>
          (c, DateTime.now().plusMinutes(delay.toMinutes.toInt))
      }

      deliveredInstantly.map {
        case true =>
          digest
        case false =>
          digest ++ digestIfNotInstantly
      }.map {
        delayed =>
          river.storage.delay(n, delayed)
      }
  }
}

object Flow {

  case class Fired(event: Event)

  case class Stored(event: Event)

  case class Notify(event: Event, notification: Notification)

}

