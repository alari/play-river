package river.channel

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author alari
 * @since 4/7/14
 */
trait Channel {
  def id: String
}

object Channel {
  trait Instant[V] extends Channel{
    type InstantVM = V
    def instant(vm: InstantVM)(implicit ec: ExecutionContext): Future[Boolean]
  }

  trait Digest[V] extends Channel {
    type DigestVM = V
    def digest(vm: DigestVM)(implicit ec: ExecutionContext): Future[Boolean]
  }
}

