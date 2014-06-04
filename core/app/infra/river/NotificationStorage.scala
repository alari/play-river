package infra.river

import scala.concurrent.{Future, ExecutionContext}
import infra.river.data.{NotificationCase, Event, Notification}
import play.api.libs.iteratee.{Iteratee, Enumerator, Enumeratee}
import org.joda.time.DateTime

/**
 * @author alari
 * @since 4/8/14
 */
trait NotificationStorage {
  def insert(implicit ec: ExecutionContext): Enumeratee[Notification, Notification]

  def findForFinder(implicit ec: ExecutionContext): Enumeratee[Finder, Notification]

  def scheduleDigest(implicit ec: ExecutionContext): Iteratee[(Notification, Seq[(String, DateTime)]), Unit]

  def act(implicit ec: ExecutionContext): Enumeratee[Watcher.Action, (Event, Notification)] = Enumeratee.mapFlatten(action _)

  /**
   * Chaining watcher actions -- e.g. read first, notify then
   * @param a
   * @param ec
   * @return
   */
  def action(a: Watcher.Action)(implicit ec: ExecutionContext): Enumerator[(Event,Notification)] = {
    val enum = a match {
      case Watcher.Push(e, n, _) =>
        Enumerator(n) &> insert ><> Enumeratee.map(nn => (e, nn))
      case Watcher.Transient(e, n, _) =>
        Enumerator((e, n))
      case Watcher.View(f, _) =>
        Enumerator.flatten(markViewed(f).map(_ => Enumerator.empty[(Event,Notification)]))
      case Watcher.Remove(f, _) =>
        Enumerator.flatten(remove(f).map(_ => Enumerator.empty[(Event,Notification)]))
    }
    a.nextAction match {
      case Some(next) =>
        enum >>> action(next)
      case None =>
        enum
    }
  }

  def markViewed(finder: Finder)(implicit ec: ExecutionContext): Future[Boolean]

  def remove(finder: Finder)(implicit ec: ExecutionContext): Future[Boolean]

  def count(finder: Finder)(implicit ec: ExecutionContext): Future[Long]

  def countGrouping(finder: Finder, grouping: Grouping)(implicit ec: ExecutionContext): Future[Long]

  def countContexts(finder: Finder, contexts: String*)(implicit ec: ExecutionContext): Enumerator[ContextsCount]

  def countContextsGrouping(finder: Finder, grouping: Grouping, contexts: String*)(implicit ec: ExecutionContext): Enumerator[ContextsCount]

  def pendings(implicit ec: ExecutionContext): Enumerator[PendingTopic]

  def pendingTopicNotifications(implicit ec: ExecutionContext): Enumeratee[PendingTopic, (PendingTopic, List[Notification])]

  def pendingProcessed(implicit ec: ExecutionContext): Enumeratee[PendingTopic, Boolean]
}

private[river] object NotificationStorage extends NotificationStorage {
  var stored: Vector[NotificationCase] = Vector.empty
  def readStored: Vector[Notification] = stored

  def forTopic(t: PendingTopic) =
    (n: Notification) => t.topic == n.topic && t.userId == n.userId && n.digest.exists(kv => kv._1 == t.channelId && kv._2.isBeforeNow)

  def matches(f: Finder) =
    (n: Notification) =>
      f.userId.fold(true)(_ == n.userId) &&
        f.contexts.fold(true)(_.forall(p => n.contexts.exists(_ == p))) &&
        f.digestChannel.fold(true)(n.digest.contains) &&
        f.viewed.fold(true)(_ == n.viewed) &&
        f.topic.fold(true)(_ == n.topic)

  override def pendingTopicNotifications(implicit ec: ExecutionContext): Enumeratee[PendingTopic, (PendingTopic, List[Notification])] =
    Enumeratee.mapFlatten[PendingTopic] {
      t: PendingTopic =>
        val f = forTopic(t)
        Enumerator(
          t -> readStored.filter(f).toList
        )
    }

  override def pendings(implicit ec: ExecutionContext): Enumerator[PendingTopic] =
    Enumerator(stored.filter(_.digest.exists(kv => kv._2.isBeforeNow)).groupBy(n => (n.userId, n.topic)).flatMap {
      case ((uid, t), ns) =>
        ns.flatMap(_.digest.toSeq.filter(_._2.isBeforeNow).map(_._1)).distinct.map(cid => PendingTopic(t, uid, cid))
    }.toSeq: _*)

  override def scheduleDigest(implicit ec: ExecutionContext): Iteratee[(Notification, Seq[(String, DateTime)]), Unit] = Iteratee.foreach {
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

  override def findForFinder(implicit ec: ExecutionContext): Enumeratee[Finder, Notification] =
    Enumeratee.mapFlatten {
      finder: Finder =>
        val f = matches(finder)
        Enumerator.enumerate(readStored.filter(f))
    }

  override def insert(implicit ec: ExecutionContext): Enumeratee[Notification, Notification] =
    Enumeratee.map {
      n =>
        val nc: NotificationCase = n
        stored = stored :+ nc
        nc
    }

  implicit def n2nc(n: Notification): NotificationCase = n match {
    case nc: NotificationCase => nc
    case _ => NotificationCase(
      n.eventId,
      n.userId,
      n.topic,
      n.timestamp,
      n.viewed,
      n.digest,
      n.contexts
    )
  }

  override def count(finder: Finder)(implicit ec: ExecutionContext): Future[Long] = {
    val f = matches(finder)
    Future(stored.filter(f).groupBy(_.eventId).size)
  }

  def projectGrouping(grouping: Grouping) = (n: Notification) => {
    val s = Set.empty[String]
    val se = if(grouping.eventId) s+ "event:"+n.eventId else s
    val st = if(grouping.topic) se + "topic:"+n.topic else se
    val su = if(grouping.userId) st + "user:"+n.userId else st
    if(grouping.contexts.nonEmpty) n.contexts.filter(kv => grouping.contexts.contains(kv._1)).foldLeft(su){
      case (sg, (k, v)) => sg + s"-$k:$v"
    }
  }

  override def countGrouping(finder: Finder, grouping: Grouping)(implicit ec: ExecutionContext): Future[Long] = {
    val f = matches(finder)
    val p = projectGrouping(grouping)
    Future(stored.filter(f).groupBy(p).size)
  }

  override def countContexts(finder: Finder, contexts: String*)(implicit ec: ExecutionContext): Enumerator[ContextsCount] = {
    val f = matches(finder)
    Enumerator.enumerate(stored.filter(f).groupBy(_.contexts.filter(kv => contexts.contains(kv._1))).map{
      case (k, v) =>
      ContextsCount(k, v.groupBy(_.eventId).size)
    })
  }

  override def countContextsGrouping(finder: Finder, grouping: Grouping, contexts: String*)(implicit ec: ExecutionContext): Enumerator[ContextsCount] = {
    val f = matches(finder)
    val p = projectGrouping(grouping)
    Enumerator.enumerate(stored.filter(f).groupBy(_.contexts.filter(kv => contexts.contains(kv._1))).map{
      case (k, v) =>
        ContextsCount(k, v.groupBy(p).size)
    })
  }

  override def remove(finder: Finder)(implicit ec: ExecutionContext): Future[Boolean] = {
    val f = matches(finder)
    Future {
      stored = stored.filterNot(f)
      true
    }
  }

  override def markViewed(finder: Finder)(implicit ec: ExecutionContext): Future[Boolean] = {
    val f = matches(finder)
    Future {
      stored = stored.map {
        case n if f(n) => n.copy(viewed = true)
        case n => n
      }
      true
    }
  }
}