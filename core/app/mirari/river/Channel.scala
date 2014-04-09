package mirari.river

import scala.concurrent.{Future, ExecutionContext}


/**
 * @author alari
 * @since 4/7/14
 */
trait Channel {
  def id: String


  def instant(implicit ec: ExecutionContext): PartialFunction[Any, Future[Boolean]] = PartialFunction.empty

  def digest(implicit ec: ExecutionContext): PartialFunction[Any, Future[Boolean]] = PartialFunction.empty
}