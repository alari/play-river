package mirari.river.channel

import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee.Enumeratee


/**
 * @author alari
 * @since 4/7/14
 */
trait Channel {
  def id: String


  def instant(view: Any)(implicit ec: ExecutionContext): Future[Boolean] = Future.successful(false)

  def digest(view: Any)(implicit ec: ExecutionContext) = Future.successful(false)
}