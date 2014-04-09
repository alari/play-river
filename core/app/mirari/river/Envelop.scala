package mirari.river

import scala.concurrent.duration.Duration

/**
 * @author alari
 * @since 4/8/14
 */
abstract sealed class Envelop {
  def channelId: String
}

object Envelop {

  sealed trait Delay extends Envelop {
    def delay: Duration
  }

  case class Instantly[V](channelId: String, view: V) extends Envelop

  case class DigestIfNotInstantly[V](channelId: String, delay: Duration) extends Delay

  case class Digest(channelId: String, delay: Duration) extends Delay

}