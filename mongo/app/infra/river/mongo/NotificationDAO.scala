package infra.river.mongo

import infra.mongo.{MongoStreams, MongoDAO}
import play.api.libs.json._
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee.{Iteratee, Enumerator, Enumeratee}
import infra.river.data.Notification
import infra.river._
import org.joda.time.DateTime
import reactivemongo.bson._
import reactivemongo.core.commands._
import play.modules.reactivemongo.json.BSONFormats.BSONDocumentFormat
import play.api.Plugin
import reactivemongo.core.commands.Group
import reactivemongo.core.commands.Match
import infra.river.Finder
import play.api.libs.json.JsString
import reactivemongo.bson.BSONString
import reactivemongo.core.commands.SumValue
import reactivemongo.bson.BSONLong
import reactivemongo.bson.BSONInteger
import reactivemongo.core.commands.Unwind
import reactivemongo.core.commands.Project
import reactivemongo.core.commands.GroupField
import reactivemongo.core.commands.GroupMulti
import infra.river.PendingTopic
import play.api.libs.json.JsObject

/**
 * @author alari
 * @since 4/14/14
 */
object NotificationDAO extends MongoDAO.Oid[NotificationDomain]("river.notification") with MongoStreams[NotificationDomain] {
  implicit val pendingF = Json.format[NotificationPending]
  protected implicit val format = Json.format[NotificationDomain]

  ensureIndex("viewed" -> Descending, "pending.delayedTill" -> Ascending)
  ensureIndex("pending.delayedTill" -> Ascending)
  ensureIndex("userId" -> Ascending, "topic" -> Ascending, "pending.channelId" -> Ascending)
  ensureIndex("contexts" -> Ascending, "userId" -> Ascending)

  def nd2nee(implicit ec: ExecutionContext) = Enumeratee.map[NotificationDomain](nd2n)

  def nd2n(nd: NotificationDomain): Notification = nd: Notification

  override def insert(n: NotificationDomain)(implicit ec: ExecutionContext) = super.insert(n)

  def markProcessed(topic: PendingTopic)(implicit ec: ExecutionContext): Future[Boolean] =
    collection.update(topic: JsObject, Json.obj("$pull" -> Json.obj("pending" -> Json.obj("channelId" -> topic.channelId))), multi = true).map(failOrTrue)

  def delay(n: NotificationDomain, delays: Seq[(String, DateTime)])(implicit ec: ExecutionContext) =
    set(n.id, Json.obj("pending" -> delays.map(ab => NotificationPending(ab._1, ab._2))))

  def pendings(implicit ec: ExecutionContext): Enumerator[PendingTopic] = {
    import reactivemongo.bson.{BSONDocument => bd}
    val delayedTill = "pending.delayedTill" -> bd("$lt" -> BSONLong(System.currentTimeMillis()))

    Enumerator.flatten(db.command(Aggregate(collectionName, Seq(
      Match(bd("viewed" -> false, delayedTill)),
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
        "channelId" -> "pending.channelId"
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
      _.headOption.map {
        doc =>
          val d = BSONDocumentFormat.writes(doc)
          (d \ "n").asOpt[Long].getOrElse {
            play.api.Logger.error(s"[river] Cannot count $finder, got " + d)
            -1l
          }
      }.getOrElse(0)
    }
  }

  def countContexts(finder: Finder, contexts: String*)(implicit ec: ExecutionContext): Enumerator[ContextsCount] = {
    val f = BSONDocumentFormat.reads(finder).asOpt.getOrElse(BSONDocument(Seq()))
    Enumerator flatten db.command(Aggregate(collectionName, Seq(
      Match(f),
      GroupMulti(contexts.map(c => c -> s"contexts.$c") :+ ("eventId" -> "eventId"): _*)(),
      GroupMulti(contexts.map(c => c -> s"_id.$c"): _*)("n" -> SumValue(1))
    ))).map {
      s =>
        Enumerator enumerate s.map {
          doc =>
            val d = BSONDocumentFormat.writes(doc)
            val ctx = (d \ "_id").asOpt[JsObject].getOrElse(Json.obj()).value.mapValues {
              case JsString(s) => s
              case _ => ""
            }.toMap
            val cnt = (d \ "n").asOpt[Long].getOrElse {
              play.api.Logger.error(s"Cannot count grouping for $finder, $contexts, got " + d)
              -1l
            }
            ContextsCount(ctx, cnt)
        }
    }
  }

  def remove(finder: Finder)(implicit ec: ExecutionContext): Future[Boolean] =
    removeAll(finder)

  def markViewed(finder: Finder)(implicit ec: ExecutionContext): Future[Boolean] =
    collection.update(finder: JsObject, Json.obj("$set" -> Json.obj("viewed" -> true))).map(failOrTrue)

  implicit def n2nd(n: Notification): NotificationDomain = n match {
    case nd: NotificationDomain => nd
    case _ =>
      NotificationDomain(
        eventId = n.eventId,
        userId = n.userId,
        topic = n.topic,
        timestamp = n.timestamp,
        viewed = n.viewed,
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
      finder.viewed.map(r => Json.obj("viewed" -> r)),
      finder.topic.map(t => Json.obj("topic" -> t)),
      finder.digestChannel.map(c => Json.obj("pending.channelId" -> c))
    ).filter(_.isDefined).map(_.get).reduce(_ ++ _)
}

/**
 * @author alari
 * @since 4/10/14
 */
class NotificationDAO(app: play.api.Application) extends Plugin with NotificationStorage {
  def dao = NotificationDAO

  override def pendingProcessed(implicit ec: ExecutionContext): Enumeratee[PendingTopic, Boolean] =
    Enumeratee.mapM(dao.markProcessed)

  override def pendingTopicNotifications(implicit ec: ExecutionContext) =
    Enumeratee.mapM(t => dao.find(dao.topicToSearch(t)).map(l => t -> l))

  override def pendings(implicit ec: ExecutionContext): Enumerator[PendingTopic] =
    dao.pendings

  override def count(finder: Finder)(implicit ec: ExecutionContext): Future[Long] =
    dao.count(finder)

  override def countContexts(finder: Finder, contexts: String*)(implicit ec: ExecutionContext) =
    dao.countContexts(finder, contexts: _*)

  override def remove(finder: Finder)(implicit ec: ExecutionContext): Future[Boolean] =
    dao.remove(finder)

  override def markViewed(finder: Finder)(implicit ec: ExecutionContext): Future[Boolean] =
    dao.markViewed(finder)

  override def scheduleDigest(implicit ec: ExecutionContext): Iteratee[(Notification, Seq[(String, DateTime)]), Unit] =
    Iteratee.foreach {
      case (n: NotificationDomain, ds) if n._id.isDefined =>
        dao.delay(n, ds)
    }

  override def findForFinder(implicit ec: ExecutionContext): Enumeratee[Finder, Notification] =
    Enumeratee.map(dao.finderToSearch) ><> dao.stream.findBy ><> dao.nd2nee

  override def insert(implicit ec: ExecutionContext): Enumeratee[Notification, Notification] =
    Enumeratee.map(dao.n2nd) ><> Enumeratee.mapM(n => dao.insert(n).mapTo[Notification])
}