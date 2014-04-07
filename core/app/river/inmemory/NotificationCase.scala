package river.inmemory

import river.data.Notification
import org.joda.time.DateTime
import river.channel.Channel

/**
 * @author alari
 * @since 4/7/14
 */
case class NotificationCase(
                             eventId: String,
                             userId: String,
                             contexts: Map[String, String] = Map(),
                             timestamp: DateTime = DateTime.now(),
                             read: Boolean = false,
                             digest: Map[String, DateTime] = Map(),
                             topic: Option[String] = None
                             ) extends Notification

object NotificationCase {
  implicit def fromN(n: Notification) = NotificationCase(
    eventId = n.eventId,
    userId = n.userId,
    contexts = n.contexts,
    timestamp = n.timestamp,
    digest = n.digest,
    topic = n.topic
  )
}