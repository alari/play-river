package river

import river.data.Notification
import scala.concurrent.{ExecutionContext, Future}

/**
 * @author alari
 * @since 4/7/14
 */
trait NotificationStore {
  def push(notification: Notification)(implicit ec: ExecutionContext): Future[Notification]

  def find(finder: NotificationsFinder)(implicit ec: ExecutionContext): Future[Seq[Notification]]

  def remove(finder: NotificationsFinder)(implicit ec: ExecutionContext): Future[Boolean]

  def count(finder: NotificationsFinder)(implicit ec: ExecutionContext): Future[Long]

  def read(finder: NotificationsFinder)(implicit ec: ExecutionContext): Future[Boolean]

  def pending()(implicit ec: ExecutionContext): Future[Seq[Notification]]
}
