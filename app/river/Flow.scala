package river

import akka.actor.Actor
import river.data.{Notification, Event}
import scala.concurrent.Future

/**
 * @author alari
 * @since 4/7/14
 */
trait Flow extends Actor with EventSystem {

  import context.dispatcher
  import Flow._


  override def receive: Receive = {
    case Fired(e) =>
      logger.store(e).map(es => self ! Stored(es))

    case Stored(e) =>
      wrapper(e).actions(e).map(_.foreach(_(store) match {
        case n: Notification =>
          self ! Notify(e, n)
      }))

    case Notify(e, n) =>
      val handler = wrapper(e)

      val deliveredInstantly = (Future sequence handler.channels.instant.map(c =>
        handler.instantView(c, (e, n)).flatMap {v =>
          c.instant(v)
        }
      )).map(_.exists(b => b))

    handler.channels.digest.foreach {
      c =>

    }

  }
}

object Flow {

  case class Fired(event: Event)

  case class Stored(event: Event)

  case class Notify(event: Event, notification: Notification)

}