package mirari.river

import scala.concurrent.ExecutionContext
import mirari.river.data.{Event, Notification}
import play.api.libs.iteratee.{Iteratee, Enumerator, Enumeratee}
import org.joda.time.DateTime

/**
 * @author alari
 * @since 4/8/14
 */
trait NotificationStorage {


  def insert(implicit ec: ExecutionContext): Enumeratee[Notification, Notification]

  def findForFinder(implicit ec: ExecutionContext): Enumeratee[Finder, Notification]

  def delay(implicit ec: ExecutionContext): Iteratee[(Notification, Seq[(String, DateTime)]), Unit]

  def act(implicit ec: ExecutionContext): Enumeratee[Watcher.Action, (Event, Notification)] = Enumeratee.mapFlatten {
    case Watcher.Push(e, n) =>
      Enumerator(n) &> insert ><> Enumeratee.map(nn => (e, nn))
    case Watcher.Read(f) =>
      // TODO
      Enumerator.empty
    case Watcher.Remove(f) =>
      // TODO
      Enumerator.empty
  }

  def pendings(implicit ec: ExecutionContext): Enumerator[PendingTopic]

  def pendingTopicNotifications(implicit ec: ExecutionContext): Enumeratee[PendingTopic, (PendingTopic, List[Notification])]

  def pendingProcessed(implicit ec: ExecutionContext): Enumeratee[PendingTopic, Boolean]
}

private[river] object NotificationStorage extends NotificationStorage {
  var stored: Vector[NotificationCase] = Vector.empty

  def forTopic(t: PendingTopic) =
    (n: Notification) => t.topic == n.topic && t.userId == n.userId && n.digest.exists(kv => kv._1 == t.channelId && kv._2.isBeforeNow)

  override def pendingTopicNotifications(implicit ec: ExecutionContext): Enumeratee[PendingTopic, (PendingTopic, List[Notification])] =
    Enumeratee.mapFlatten {
      t =>
        val f = forTopic(t)
        Enumerator(
          t -> stored.filter(f).toList
        )

    }

  override def pendings(implicit ec: ExecutionContext): Enumerator[PendingTopic] =
    Enumerator(stored.filter(_.digest.exists(kv => kv._2.isBeforeNow)).groupBy(n => (n.userId, n.topic)).flatMap {
      case ((uid, t), ns) =>
        ns.flatMap(_.digest.toSeq.filter(_._2.isBeforeNow).map(_._1)).distinct.map(cid => PendingTopic(t, uid, cid))
    }.toSeq: _*)

  override def delay(implicit ec: ExecutionContext): Iteratee[(Notification, Seq[(String, DateTime)]), Unit] = Iteratee.foreach {
    case (n, d) =>
      val nc: NotificationCase = n
      stored = stored.map {
        case no if no == n =>
          nc.copy(digest = d.toMap)
        case no => no
      }
  }


  override def pendingProcessed(implicit ec: ExecutionContext): Enumeratee[PendingTopic, Boolean] = Enumeratee.map {
    t =>
      val f = forTopic(t)
      stored = stored.map {
        case n if f(n) =>
          n.copy(digest = n.digest.filterNot(_._1 == t.channelId))
        case n => n
      }
      true
  }

  override def findForFinder(implicit ec: ExecutionContext): Enumeratee[Finder, Notification] = ???

  override def insert(implicit ec: ExecutionContext): Enumeratee[Notification, Notification] =
    Enumeratee.map {
      n =>
        val nc: NotificationCase = n
        stored = stored :+ nc
        nc
    }

  implicit def n2nc(n: Notification): NotificationCase = NotificationCase(
    n.eventId,
    n.userId,
    n.topic,
    n.timestamp,
    n.read,
    n.digest,
    n.contexts
  )

  case class NotificationCase(
                               eventId: String,
                               userId: String,
                               topic: String,
                               timestamp: DateTime = DateTime.now(),
                               read: Boolean = false,
                               digest: Map[String, DateTime] = Map.empty,
                               contexts: Map[String, String] = Map.empty
                               ) extends Notification

}