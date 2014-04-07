package river.handle

import river.data.{Notification, Event}
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.Duration
import river.NotificationStorage
import scala.reflect.ClassTag
import river.channel.Channel

/**
 * @author alari
 * @since 4/7/14
 */
trait Handler {
  self =>

  def compose(o: Handler): Handler = new Handler {
    override def actions(event: Event)(implicit ec: ExecutionContext) = self.actions(event).zip(o.actions(event)).map(ab => ab._1 ++ ab._2)

    override def channels = Handler.Channels(
      self.channels.instant ++ o.channels.instant,
      self.channels.digest ++ o.channels.digest,
      self.channels.digestIfNotInstantly ++ o.channels.digestIfNotInstantly
    )

    override def instantView[V](channel: Channel.Instant[V], event: (Event, Notification))(implicit ec: ExecutionContext, ct: ClassTag[V]) =
      self.instantView(channel, event).fallbackTo(o.instantView(channel, event))

  }

  def andThen(o: Handler) = o.compose(self)

  def isDefinedFor(event: Event): Boolean = true

  def actions(event: Event)(implicit ec: ExecutionContext): Future[Seq[NotificationStorage.Action]] =
    Future successful Seq()

  def channels: Handler.Channels =
    Handler.Channels()

  def instantView[V](channel: Channel.Instant[V], event: (Event, Notification))(implicit ec: ExecutionContext, ct: ClassTag[V]): Future[V] =
    Future.failed(new IllegalArgumentException("No view for channel: " + channel))

}

object Handler {

  case class Channels(
                       instant: Seq[Channel.Instant[_]] = Seq(),
                       digest: Seq[(Channel.Digest[_], Duration)] = Seq(),
                       digestIfNotInstantly: Seq[(Channel.Digest[_], Duration)] = Seq()
                       )

}