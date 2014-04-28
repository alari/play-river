package infra.river.data

import org.joda.time.DateTime
import infra.river.Watcher

/**
 * @author alari
 * @since 4/7/14
 */
trait Event extends Contexts with Artifacts {
  def id: String

  def userId: Option[String]

  def action: String

  def timestamp: DateTime

  def notification(userId: String, topic: String, withContexts: (String, String)*): Notification = NotificationCase.produce(this, userId, topic, withContexts.toMap)

  def push(userId: String, topic: String, withContexts: (String, String)*) = Watcher.Push(this, notification(userId, topic, withContexts: _*))

  def transient(userId: String, topic: String, withContexts: (String, String)*) = Watcher.Transient(this, notification(userId, topic, withContexts: _*))
}