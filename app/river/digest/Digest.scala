package river.digest

import akka.actor.Actor
import river.data.{Event, Notification}
import river.{NotificationStorage, River}
import river.channel.Channel

/**
 * @author alari
 * @since 4/7/14
 */
class Digest extends Actor {

  import context.dispatcher
  import Digest._

  def river: River = River

  override def receive: Receive = {
    case CheckPending =>
      river.storage.pending().map {
        ns =>
          for {
            (userId, byUser) <- ns.view.groupBy(_.userId)
            (topic, byTopic) <- byUser.groupBy(_.topic)
            channels <- byTopic.map(_.digest.filter(_._2.isBeforeNow).keys)
            c <- channels
          } {
            self ! TriggerDigest(userId, topic, c)
          }

      }

    case TriggerDigest(uid, t, c) =>
      for {
        notifications <- river.storage.find(NotificationStorage.Finder(
          userId = Some(uid),
          topic = t,
          read = Some(false),
          delayed = Some(c)
        ))
        events <- river.logger.getByIds(notifications.map(_.eventId))
      } {
        self ! BuildDigest(uid, t, c, events.view
          .map {
          e =>
            notifications.find(_.eventId == e.id).map(n => (e, n))
        }.filter(_.isDefined)
          .map(_.get).force)
      }

    case bd@BuildDigest(uid, t, c, events) =>
      river.digesters(t)
        .digestView(c, events)
        .flatMap(c.digest)
        .map {
        case true =>
          river.storage.digested(NotificationStorage.Finder(
            userId = Some(uid),
            topic = t,
            read = Some(false),
            delayed = Some(c)
          ), c)
        case false =>
          play.api.Logger.error("Cannot send a digest " + bd)
          river.storage.digested(NotificationStorage.Finder(
            userId = Some(uid),
            topic = t,
            read = Some(false),
            delayed = Some(c)
          ), c)
      }


  }
}

object Digest {

  case object CheckPending

  case class TriggerDigest[V](userId: String, topic: Option[String], channel: Channel.Digest[V])

  case class BuildDigest[V](userId: String, topic: Option[String], channel: Channel.Digest[V], events: Seq[(Event, Notification)])

}