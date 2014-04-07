package river

import scala.concurrent.{Future, ExecutionContext}
import river.data.Event

/**
 * @author alari
 * @since 4/7/14
 */
trait EventLogger {
  def store(e: Event)(implicit ec: ExecutionContext): Future[Event]

  def getById(id: String)(implicit ec: ExecutionContext): Future[Event]

  def getByIds(ids: Seq[String])(implicit ec: ExecutionContext): Future[Seq[Event]]
}