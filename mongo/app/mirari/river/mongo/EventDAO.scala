package mirari.river.mongo

import play.api.Plugin
import mirari.river.RiverLogger
import scala.concurrent.ExecutionContext
import play.api.libs.iteratee.Enumeratee
import mirari.river.data.Event
import mirari.mongo.{MongoStreams, MongoDomain, MongoDAO}
import play.api.libs.json.Json
import org.joda.time.DateTime

/**
 * @author alari
 * @since 4/10/14
 */
class EventDAO(app: play.api.Application) extends Plugin with RiverLogger {
  def dao = EventDAO

  override def insert(implicit ec: ExecutionContext): Enumeratee[Event, Event] = Enumeratee.map(dao.e2ed) ><> dao.Stream.insert ><> dao.ed2ee

  override def getByIds(implicit ec: ExecutionContext): Enumeratee[TraversableOnce[String], Event] = dao.Stream.getByIds ><> dao.ed2ee

  override def getById(implicit ec: ExecutionContext): Enumeratee[String, Event] = dao.Stream.getById ><> dao.ed2ee
}

object EventDAO extends MongoDAO.Oid[EventDomain]("river.event") with MongoStreams[EventDomain] {
  implicit protected val format = Json.format[EventDomain]

  def ed2ee(implicit ec: ExecutionContext) = Enumeratee.map[EventDomain](ed2e)

  def ed2e(ed: EventDomain): Event = ed

  implicit def e2ed(e: Event): EventDomain = e match {
    case ed: EventDomain => ed
    case _ =>
      EventDomain(
        action = e.action,
        userId = e.userId,
        contexts = e.contexts,
        artifacts = e.artifacts,
        timestamp = e.timestamp,
        _id = generateSomeId
      )
  }
}

case class EventDomain(action: String,
                       userId: Option[String],

                       contexts: Map[String, String],
                       artifacts: Map[String, String],

                       timestamp: DateTime,

                       _id: MongoDomain.Oid.Id
                        ) extends MongoDomain.Oid with Event