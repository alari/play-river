package mirari.river.mongo

import mirari.river._
import mirari.river.data._

import play.api.Plugin
import mirari.mongo.{MongoStreams, MongoDomain, MongoDAO}
import org.joda.time.DateTime
import play.api.libs.json.Json
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee.{Enumerator, Iteratee, Enumeratee}
import reactivemongo.core.commands._
import reactivemongo.bson._
import reactivemongo.core.commands.Match
import mirari.river.Finder
import play.api.libs.json.JsString
import reactivemongo.bson.BSONInteger
import reactivemongo.core.commands.Unwind
import reactivemongo.core.commands.Project
import reactivemongo.core.commands.GroupMulti
import mirari.river.PendingTopic
import play.api.libs.json.JsObject
import play.modules.reactivemongo.json.BSONFormats.BSONDocumentFormat

/**
 * @author alari
 * @since 4/10/14
 */
class NotificationDAO extends Plugin with NotificationStorage {
  def dao = NotificationDAO

  override def pendingProcessed(implicit ec: ExecutionContext): Enumeratee[PendingTopic, Boolean] =
    Enumeratee.mapM(dao.markProcessed)

  override def pendingTopicNotifications(implicit ec: ExecutionContext): Enumeratee[PendingTopic, (PendingTopic, List[Notification])] =
    Enumeratee.mapM(t => dao.find(dao.topicToSearch(t)).map(l => t -> l))

  override def pendings(implicit ec: ExecutionContext): Enumerator[PendingTopic] =
    dao.pendings

  override def count(finder: Finder)(implicit ec: ExecutionContext): Future[Long] =
    dao.count(finder)

  override def remove(finder: Finder)(implicit ec: ExecutionContext): Future[Boolean] =
  dao.remove(finder)

  override def markRead(finder: Finder)(implicit ec: ExecutionContext): Future[Boolean] =
  dao.markRead(finder)

  override def delay(implicit ec: ExecutionContext): Iteratee[(Notification, Seq[(String, DateTime)]), Unit] = Iteratee.foreach {
    case (n: NotificationDomain, ds) =>
      dao.delay(n, ds)
  }

  override def findForFinder(implicit ec: ExecutionContext): Enumeratee[Finder, Notification] = Enumeratee.map(dao.finderToSearch) ><> dao.Stream.findBy ><> dao.nd2nee

  override def insert(implicit ec: ExecutionContext): Enumeratee[Notification, Notification] = Enumeratee.map(dao.n2nd) ><> dao.Stream.insert ><> dao.nd2nee
}

object NotificationDAO extends MongoDAO.Oid[NotificationDomain]("river.notifications") with MongoStreams[NotificationDomain] {
  implicit val pendingF = Json.format[NotificationPending]
  protected implicit val format = Json.format[NotificationDomain]

  def nd2nee(implicit ec: ExecutionContext) = Enumeratee.map[NotificationDomain](nd2n)

  def nd2n(nd: NotificationDomain): Notification = nd: Notification

  def markProcessed(topic: PendingTopic)(implicit ec: ExecutionContext): Future[Boolean] =
    collection.update(topic: JsObject, Json.obj("$unset" -> Json.obj(s"digest.${topic.channelId}" -> true)), multi = true).map(failOrTrue)

  def delay(n: NotificationDomain, delays: Seq[(String, DateTime)])(implicit ec: ExecutionContext) =
    set(n.id, Json.obj("pending" -> delays.map(ab => NotificationPending(ab._1, ab._2))))

  def pendings(implicit ec: ExecutionContext): Enumerator[PendingTopic] = {
    import reactivemongo.bson.{BSONDocument => bd}
    val delayedTill = "pending.delayedTill" -> bd("$lt" -> BSONDateTime(System.currentTimeMillis()))

    Enumerator.flatten(db.command(Aggregate(collectionName, Seq(
      Match(bd("read" -> false, delayedTill)),
      Project(
        "topic" -> BSONInteger(1),
        "userId" -> BSONInteger(1),
        "pending" -> BSONInteger(1)
      ),
      Unwind("pending"),
      Match(bd(delayedTill)),
      GroupMulti(
        "topic" -> "topic",
        "userId" -> "userId",
        "pending.channelId" -> "channelId"
      )()
    ))).map {
      _.map {
        _.get("_id") flatMap {
          case b: BSONDocument =>
            for {
              t <- b.getAs[BSONString]("topic").map(_.value)
              u <- b.getAs[BSONString]("userId").map(_.value)
              c <- b.getAs[BSONString]("channelId").map(_.value)
            } yield PendingTopic(t, u, c)
        }
      }.filter(_.isDefined).map(_.get)
    }.map(Enumerator.enumerate[PendingTopic]))

  }

  def count(finder: Finder)(implicit ec: ExecutionContext): Future[Long] = {
    val f = BSONDocumentFormat.reads(finder).asOpt.getOrElse(BSONDocument(Seq()))
    db.command(Aggregate(collectionName, Seq(
      Match(f),
      GroupField("eventId")(),
      Group(BSONInteger(0))("n" -> SumValue(1))
    ))).map {
      _.headOption.flatMap(_.getAs[BSONLong]("n").map(_.value)).getOrElse(-1)
    }
  }

  def remove(finder: Finder)(implicit ec: ExecutionContext): Future[Boolean] =
    removeAll(finder)

  def markRead(finder: Finder)(implicit ec: ExecutionContext): Future[Boolean] =
    collection.update(finder: JsObject, Json.obj("$set" -> Json.obj("read" -> true))).map(failOrTrue)

  implicit def n2nd(n: Notification): NotificationDomain = n match {
    case nd: NotificationDomain => nd
    case _ => NotificationDomain(
      eventId = n.eventId,
      userId = n.userId,
      topic = n.topic,
      timestamp = n.timestamp,
      read = n.read,
      pending = n.digest.map(ab => NotificationPending(ab._1, ab._2)).toSeq,
      contexts = n.contexts,
      _id = generateSomeId
    )
  }

  implicit def topicToSearch(topic: PendingTopic): JsObject =
    Json.obj(
      "userId" -> topic.userId,
      "topic" -> topic.topic,
      "pending.channelId" -> topic.channelId
    )

  implicit def finderToSearch(finder: Finder): JsObject =
    Seq(
      finder.userId.map(uid => Json.obj("userId" -> uid)),
      finder.contexts.map(ctx => JsObject(ctx.map {
        case (k, v) => s"contexts.$k" -> JsString(v)
      }.toSeq)),
      finder.read.map(r => Json.obj("read" -> r)),
      finder.topic.map(t => Json.obj("topic" -> t)),
      finder.delayed.map(c => Json.obj("pending.channelId" -> c))
    ).filter(_.isDefined).map(_.get).reduce(_ ++ _)
}

case class NotificationDomain(
                               eventId: String,
                               userId: String,
                               topic: String,
                               timestamp: DateTime,
                               read: Boolean,
                               pending: Seq[NotificationPending],
                               contexts: Map[String, String],
                               _id: MongoDomain.Oid.Id
                               ) extends MongoDomain.Oid with Notification {
  lazy val digest = pending.map(nd => nd.channelId -> nd.delayedTill).toMap
}

case class NotificationPending(channelId: String, delayedTill: DateTime)