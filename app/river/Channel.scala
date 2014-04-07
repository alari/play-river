package river

import scala.concurrent.duration.Duration
import scala.concurrent.Future

/**
 * @author alari
 * @since 4/7/14
 */
trait Channel {
  def id: String
}

object Channel {
  sealed trait Action

  case object Throttle extends Action
  case object Instantly extends Action
  case class Digest(delay: Duration) extends Action
  case class DigestIfNotDeliveredInstantly(delay: Duration) extends Action
}

trait InstantChannel[V] {
  self: Channel =>
  def instant(vm: V): Future[Boolean]
}

trait DigestChannel[V] {
  self: Channel =>
  def digest(vm: V): Future[Boolean]
}