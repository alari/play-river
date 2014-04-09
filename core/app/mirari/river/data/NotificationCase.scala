package mirari.river.data

import org.joda.time.DateTime

/**
 * @author alari
 * @since 4/9/14
 */
case class NotificationCase(
                             eventId: String,
                             userId: String,
                             topic: String,
                             timestamp: DateTime = DateTime.now(),
                             read: Boolean = false,
                             digest: Map[String, DateTime] = Map.empty,
                             contexts: Map[String, String] = Map.empty
                             ) extends Notification