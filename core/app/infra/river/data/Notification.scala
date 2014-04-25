package infra.river.data

import org.joda.time.DateTime

/**
 * @author alari
 * @since 4/7/14
 */
trait Notification extends Contexts {
  def eventId: String

  def userId: String

  def timestamp: DateTime

  def viewed: Boolean

  def digest: Map[String, DateTime]

  def topic: String
}
