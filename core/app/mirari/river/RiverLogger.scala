package mirari.river

import scala.concurrent.ExecutionContext
import play.api.libs.iteratee.{Enumerator, Enumeratee}
import mirari.river.data.Event

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
  var events: Seq[Event] = Seq.empty

  override def insert(implicit ec: ExecutionContext): Enumeratee[Event, Event] =
    Enumeratee.map[Event] {
      e =>
        events :+= e
        e
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
        events.find(_.id == id).map(Enumerator(_)).getOrElse(Enumerator.empty)
    }
}