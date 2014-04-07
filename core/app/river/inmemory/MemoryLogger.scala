package river.inmemory

import river.EventLogger
import river.data.Event
import scala.concurrent.{Future, ExecutionContext}

/**
 * @author alari
 * @since 4/7/14
 */
object MemoryLogger extends EventLogger{
  var storage: Seq[Event] = Seq()

  override def getByIds(ids: Seq[String])(implicit ec: ExecutionContext): Future[Seq[Event]] = Future(storage.filter(s => ids.contains(s.id)))

  override def getById(id: String)(implicit ec: ExecutionContext): Future[Event] =
    storage
      .find(_.id == id)
      .map(Future.successful)
      .getOrElse(Future.failed(new IllegalArgumentException("Empty")))

  override def store(e: Event)(implicit ec: ExecutionContext): Future[Event] = {
    storage :+= e
    Future.successful(e)
  }
}
