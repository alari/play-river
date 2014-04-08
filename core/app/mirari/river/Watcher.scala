package mirari.river

import play.api.libs.iteratee.Enumerator
import mirari.river.data.{Notification, Event}
import scala.concurrent.ExecutionContext

/**
 * @author alari
 * @since 4/8/14
 */
trait Watcher {
  def apply(e: Event)(implicit ec: ExecutionContext): Enumerator[Watcher.Action]
}

object Watcher {

  abstract sealed class Action

  case class Push(event: Event, notification: Notification) extends Action

  case class Remove(finder: Finder) extends Action

  case class Read(finder: Finder) extends Action

}
