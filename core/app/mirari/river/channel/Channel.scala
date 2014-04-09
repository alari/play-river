package mirari.river.channel

import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee.Enumeratee


/**
 * @author alari
 * @since 4/7/14
 */
trait Channel {
  def id: String


  def instant(implicit ec: ExecutionContext): PartialFunction[Any,Future[Boolean]] = PartialFunction.empty

  def digest(implicit ec: ExecutionContext): PartialFunction[Any,Future[Boolean]] = PartialFunction.empty
}