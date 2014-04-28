package infra.river.data

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
                             viewed: Boolean = false,
                             digest: Map[String, DateTime] = Map.empty,
                             contexts: Map[String, String] = Map.empty
                             ) extends Notification

object NotificationCase {
  def produce(event: Event, userId: String, topic: String, withContexts: Map[String,String] = Map.empty) =
    NotificationCase(event.id, userId, topic, contexts = event.contexts ++ event.artifacts ++ withContexts, viewed = event.userId.exists(_ == userId))
}