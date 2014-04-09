package mirari.river

import scala.concurrent.{Future, ExecutionContext}
import mirari.river.data.{Notification, Event}

/**
 * @author alari
 * @since 4/8/14
 */
trait DigestView {
  def toDigest(implicit ec: ExecutionContext): PartialFunction[(PendingTopic,Seq[(Event,Notification)]),Future[Any]]
}
