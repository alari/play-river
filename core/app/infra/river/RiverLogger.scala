package infra.river

import scala.concurrent.ExecutionContext
import play.api.libs.iteratee.{Enumerator, Enumeratee}
import infra.river.data.{EventCase, Event}
import java.util.UUID

/**
 * @author alari
 * @since 4/8/14
 */
trait RiverLogger {
  def getById(implicit ec: ExecutionContext): Enumeratee[String, Event]

  def getByIds(implicit ec: ExecutionContext): Enumeratee[TraversableOnce[String], Event]

  def insert(implicit ec: ExecutionContext): Enumeratee[Event, Event]
}

private[river] object RiverLogger extends RiverLogger {
  var events: Seq[EventCase] = Seq.empty

  override def insert(implicit ec: ExecutionContext): Enumeratee[Event, Event] =
    Enumeratee.map[Event] {
      e =>
        val ec: EventCase = e
        events :+= ec
        ec
    }

  override def getByIds(implicit ec: ExecutionContext): Enumeratee[TraversableOnce[String], Event] =
    Enumeratee.mapFlatten {
      ids =>
        val idsl = ids.toSet
        Enumerator(events.filter(e => idsl.contains(e.id)): _*)
    }

  override def getById(implicit ec: ExecutionContext): Enumeratee[String, Event] =
    Enumeratee.mapFlatten {
      id =>
        events.find(_.id == id).map(Enumerator(_: Event)).getOrElse(Enumerator.empty)
    }

  implicit def e2ec(e: Event): EventCase = e match {
    case ec: EventCase if ec.id == null => ec.copy(id = UUID.randomUUID().toString)
    case ec: EventCase => ec
    case _ => EventCase(
      action = e.action,
      userId = e.userId,

      contexts = e.contexts,
      artifacts = e.artifacts,

      timestamp = e.timestamp,

      id = if (e.id == null) UUID.randomUUID().toString else e.id
    )
  }


}