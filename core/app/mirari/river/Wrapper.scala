package mirari.river

import mirari.river.data.{Event, Notification}
import scala.concurrent.ExecutionContext
import play.api.libs.iteratee.Enumerator

/**
 * @author alari
 * @since 4/8/14
 */
trait Wrapper {
  def apply(event: Event, notification: Notification)(implicit ec: ExecutionContext): Enumerator[Envelop]
}
