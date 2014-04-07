package river.data

import org.joda.time.DateTime

/**
 * @author alari
 * @since 4/7/14
 */
trait Event extends Contexts with Artifacts {
  def id: String

  def userId: Option[String]

  def action: String

  def timestamp: DateTime
}