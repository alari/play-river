package river.inmemory

import river.NotificationStorage
import river.data.Notification
import scala.concurrent.{Future, ExecutionContext}
import river.NotificationStorage.Finder
import river.channel.Channel
import Channel.Digest
import org.joda.time.DateTime

/**
 * @author alari
 * @since 4/7/14
 */
object MemoryStorage extends NotificationStorage{
  var storage: Seq[NotificationCase] = Seq()

  override def pending()(implicit ec: ExecutionContext): Future[Seq[Notification]] =
    Future.successful(storage.filter(_.digest.values.exists(_.isBeforeNow)))

  override def digested(finder: Finder, channel: Digest[_]): Future[Boolean] = {
    val f = found(finder)
    storage = storage.map {
      case n if f(n) =>
        n.copy(digest =  n.digest.filter(_._1 != channel.id))
      case n => n
    }
    Future.successful(true)
  }


  override def delay(notification: Notification, digest: Seq[(Digest[_], DateTime)]): Future[Boolean] = {
    storage = storage.map {
      case n if n == notification =>
        n.copy(digest = digest.map{case (k, v) => k.id -> v}.toMap)
      case n => n
    }
    Future.successful(true)
  }

  override def read(finder: Finder)(implicit ec: ExecutionContext): Future[Boolean] = {
    val f = found(finder)
    storage = storage.map {
      case n if f(n) =>
        n.copy(read = true)
      case n => n
    }
    Future.successful(true)
  }

  override def count(finder: Finder)(implicit ec: ExecutionContext): Future[Long] = {
    val f = found(finder)
    Future(storage.count(f))
  }

  override def remove(finder: Finder)(implicit ec: ExecutionContext): Future[Boolean] = {
    val f = found(finder)
    storage = storage.filterNot(f)
    Future successful true
  }

  override def find(finder: Finder)(implicit ec: ExecutionContext): Future[Seq[Notification]] = {
    val f = found(finder)
    Future(storage.filter(f))
  }

  override def push(notification: Notification)(implicit ec: ExecutionContext): Future[Notification] = {
    storage = storage :+ (notification: NotificationCase)
    Future.successful(notification)
  }

  def found(finder: Finder) = (n: Notification) => {
    finder.contexts.map{c=> c.forall(ci => n.contexts.exists(_ == ci))}.getOrElse(true) &&
    finder.delayed.map(d => n.digest.exists(_._1 == d.id)).getOrElse(true) &&
    finder.read.map(r => n.read == r).getOrElse(true) &&
    finder.topic.map(r => n.topic.exists(_ == r)).getOrElse(true) &&
    finder.userId.map(_ == n.userId).getOrElse(true)
  }

}
