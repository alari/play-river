package river

import river.data.Notification
import scala.concurrent.{Future, ExecutionContext}

/**
 * @author alari
 * @since 4/7/14
 */
abstract sealed class Action {
  def apply(store: NotificationStore)(implicit ec: ExecutionContext): Future[Any] = this match {
    case PushNotification(n) =>
      store.push(n)
    case MarkNotificationsRead(f) =>
      store.read(f)
    case RemoveNotifications(f) =>
      store.remove(f)
  }
}

case class PushNotification(notification: Notification) extends Action

case class MarkNotificationsRead(finder: NotificationsFinder) extends Action

case class RemoveNotifications(finder: NotificationsFinder) extends Action