package infra.river.mongo

import infra.mongo.{MongoStreams, MongoDAO}
import play.api.libs.json.Json
import scala.concurrent.ExecutionContext
import play.api.libs.iteratee.Enumeratee
import infra.river.data.Event
import play.api.Plugin
import infra.river.RiverLogger

/**
 * @author alari
 * @since 4/14/14
 */
object EventDAO extends MongoDAO.Oid[EventDomain]("river.event") with MongoStreams[EventDomain] {
  implicit protected val format = Json.format[EventDomain]

  override def insert(e: EventDomain)(implicit ec: ExecutionContext) = super.insert(e)

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

/**
 * @author alari
 * @since 4/10/14
 */
class EventDAO(app: play.api.Application) extends Plugin with RiverLogger {
  def dao = EventDAO

  override def insert(implicit ec: ExecutionContext): Enumeratee[Event, Event] = Enumeratee.mapM(e => dao.insert(dao.e2ed(e)).mapTo[Event])

  override def getByIds(implicit ec: ExecutionContext): Enumeratee[TraversableOnce[String], Event] = dao.stream.getByIds ><> dao.ed2ee

  override def getById(implicit ec: ExecutionContext): Enumeratee[String, Event] = dao.stream.getById ><> dao.ed2ee
}