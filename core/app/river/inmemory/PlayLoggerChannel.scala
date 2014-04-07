package river.inmemory

import scala.concurrent.{ExecutionContext, Future}
import river.channel.Channel


/**
 * @author alari
 * @since 4/7/14
 */
object PlayLoggerChannel extends Channel with Channel.Instant[String] with Channel.Digest[String] {
  val id = "play-logger"

  override def instant(vm: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    play.api.Logger.debug("INSTANT: "+vm)
    Future.successful(true)
  }

  override def digest(vm: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    play.api.Logger.info("DIGEST: " + vm)
    Future.successful(true)
  }
}
