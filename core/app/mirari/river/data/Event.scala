package mirari.river.data

import org.joda.time.DateTime
import mirari.river.Watcher

/**
 * @author alari
 * @since 4/7/14
 */
trait Event extends Contexts with Artifacts {
  def id: String

  def userId: Option[String]

  def action: String

  def timestamp: DateTime

  def notification(userId: String, topic: String): Notification = NotificationCase.produce(this, userId, topic)

  def push(userId: String, topic: String) = Watcher.Push(this, notification(userId, topic))
}