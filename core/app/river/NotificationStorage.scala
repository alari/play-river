package river

import river.data.Notification
import scala.concurrent.{ExecutionContext, Future}
import org.joda.time.DateTime
import river.channel.Channel

/**
 * @author alari
 * @since 4/7/14
 */
trait NotificationStorage {
  def push(notification: Notification)(implicit ec: ExecutionContext): Future[Notification]

  def find(finder: NotificationStorage.Finder)(implicit ec: ExecutionContext): Future[Seq[Notification]]

  def remove(finder: NotificationStorage.Finder)(implicit ec: ExecutionContext): Future[Boolean]

  def count(finder: NotificationStorage.Finder)(implicit ec: ExecutionContext): Future[Long]

  def read(finder: NotificationStorage.Finder)(implicit ec: ExecutionContext): Future[Boolean]

  def delay(notification: Notification, digest: Seq[(Channel.Digest[_], DateTime)]): Future[Boolean]

  def digested(finder: NotificationStorage.Finder, channel: Channel.Digest[_]): Future[Boolean]

  def pending()(implicit ec: ExecutionContext): Future[Seq[Notification]]
}

object NotificationStorage {

  abstract sealed class Action {
    def apply(store: NotificationStorage)(implicit ec: ExecutionContext): Future[Any] = this match {
      case PushNotification(n) =>
        store.push(n)
      case MarkNotificationsRead(f) =>
        store.read(f)
      case RemoveNotifications(f) =>
        store.remove(f)
    }
  }

  case class PushNotification(notification: Notification) extends Action

  case class MarkNotificationsRead(finder: Finder) extends Action

  case class RemoveNotifications(finder: Finder) extends Action

  case class Finder(
                     userId: Option[String] = None,
                     contexts: Option[Map[String, String]] = None,
                     read: Option[Boolean] = None,
                     topic: Option[String] = None,
                     delayed: Option[Channel.Digest[_]] = None
                     )

}