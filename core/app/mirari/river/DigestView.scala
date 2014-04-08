package mirari.river

import scala.concurrent.{Future, ExecutionContext}
import mirari.river.data.{Notification, Event}

/**
 * @author alari
 * @since 4/8/14
 */
trait DigestView {
  def apply(topic: PendingTopic, items: Seq[(Event,Notification)])(implicit ec: ExecutionContext): Option[Future[Any]]
}
