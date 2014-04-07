package river.inmemory

import river.digest.Digester
import river.channel.Channel
import Channel.Digest
import river.data.{Notification, Event}
import scala.concurrent.{Future, ExecutionContext}
import scala.reflect.ClassTag

/**
 * @author alari
 * @since 4/7/14
 */
object AnyDigester extends Digester {
  override def digestView[V](channel: Digest[V], events: Seq[(Event, Notification)])(implicit ec: ExecutionContext, ct: ClassTag[V]): Future[V] = channel match {
    case PlayLoggerChannel =>
      Future.successful(events.toString()).mapTo[V]
    case _ =>
      Future.failed(new IllegalArgumentException("Not available"))
  }
}
