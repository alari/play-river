package river.inmemory

import river.handle.Handler
import river.handle.Handler.Channels
import river.data.{Notification, Event}
import scala.concurrent.{Future, ExecutionContext}
import river.NotificationStorage.{PushNotification, Action}
import river.channel.Channel
import Channel.Instant
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

/**
 * @author alari
 * @since 4/7/14
 */
object AnyHandler extends Handler {
  override def instantView[V](channel: Instant[V], event: (Event, Notification))(implicit ec: ExecutionContext, ct: ClassTag[V]): Future[V] = channel match {
    case PlayLoggerChannel =>
      Future.successful( event.toString() ).mapTo[V]
    case _ =>
      Future failed new IllegalArgumentException("Not defined")

  }

  override def actions(event: Event)(implicit ec: ExecutionContext): Future[Seq[Action]] =
    Future successful Seq(PushNotification(NotificationCase(
      event.id,
      "root",
      event.contexts ++ event.artifacts,
      topic = Some("check")
    )))

  override val channels = Channels(
    instant = Seq(PlayLoggerChannel),
    digest = Seq(PlayLoggerChannel -> Duration(1, "seconds"))
  )
}
