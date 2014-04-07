package river

import river.data.{Notification, Event}
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.Duration

/**
 * @author alari
 * @since 4/7/14
 */
trait Handler {
  self =>

  final def compose(o: Handler): Handler = new Handler {
    override def actions(event: Event)(implicit ec: ExecutionContext) = self.actions(event).zip(o.actions(event)).map(ab => ab._1 ++ ab._2)

    override def channels = Handler.Channels(
      self.channels.instant ++ o.channels.instant,
      self.channels.digest ++ o.channels.digest,
      self.channels.digestIfNotInstantly ++ o.channels.digestIfNotInstantly
    )

    override def instantView[V](channel: InstantChannel[V], event: (Event, Notification))(implicit ec: ExecutionContext): Future[V] =
      self.instantView(channel, event).fallbackTo(o.instantView(channel, event))

    override def digestView[V](channel: DigestChannel[V], events: Seq[(Event, Notification)])(implicit ec: ExecutionContext): Future[V] =
      self.digestView(channel, events).fallbackTo(o.digestView(channel, events))
  }

  final def andThen(o: Handler) = o.compose(self)

  def actions(event: Event)(implicit ec: ExecutionContext): Future[Seq[Action]] =
    Future successful Seq()

  def channels: Handler.Channels =
    Handler.Channels()

  def instantView[V](channel: InstantChannel[V], event: (Event, Notification))(implicit ec: ExecutionContext): Future[V] =
    Future.failed(new IllegalArgumentException("No view for channel: " + channel))

  def digestView[V](channel: DigestChannel[V], events: Seq[(Event, Notification)])(implicit ec: ExecutionContext): Future[V] =
    Future.failed(new IllegalArgumentException("No view for channel: " + channel))

}

object Handler {

  case class Channels(
                       instant: Seq[_ <: InstantChannel[_]] = Seq(),
                       digest: Seq[(DigestChannel[_], Duration)] = Seq(),
                       digestIfNotInstantly: Seq[(DigestChannel[_], Duration)] = Seq()
                       )

}