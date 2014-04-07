package river.data

import org.joda.time.DateTime
import river.channel.Channel

/**
 * @author alari
 * @since 4/7/14
 */
trait Notification extends Contexts {
  def eventId: String
  def userId: String
  def timestamp: DateTime
  def read: Boolean
  def digest: Map[Channel.Digest[_],DateTime]
  def topic: Option[String]
}
