package river.digest

import river.data.{Notification, Event}
import scala.concurrent.{Future, ExecutionContext}
import scala.reflect.ClassTag
import river.channel.Channel

/**
 * @author alari
 * @since 4/7/14
 */
trait Digester {
  self =>

  def isDefinedFor(topic: Option[String]) = true

  def compose(o: Digester) = new Digester {
    override def digestView[V](channel: Channel.Digest[V], events: Seq[(Event, Notification)])(implicit ec: ExecutionContext, ct: ClassTag[V]) =
      self.digestView(channel, events).fallbackTo(o.digestView(channel, events))
  }

  def andThen(o: Digester) = o.compose(self)

  def digestView[V](channel: Channel.Digest[V], events: Seq[(Event, Notification)])(implicit ec: ExecutionContext, ct: ClassTag[V]): Future[V] =
    Future.failed(new IllegalArgumentException("No view for channel: " + channel))
}
