package mirari.river

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.test.{FakeApplication, WithApplication}
import play.api.Plugin
import mirari.river.data.{EventCase, NotificationCase, Notification, Event}
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee.Enumerator
import scala.concurrent.duration._
import mirari.river.channel.Channel

/**
 * @author alari
 * @since 4/8/14
 */
@RunWith(classOf[JUnitRunner])
class ReverSpec extends Specification {
  "river" should {
    "work" in new WithApplication(FakeApplication(additionalPlugins = Seq(
      "mirari.river.TestWatcher",
      "mirari.river.TestWrapper",
      "mirari.river.TestDigestView",
      "mirari.river.TestChannel"
    ))) {
      (1 to 4000).foreach {
        i =>
          //play.api.Logger.warn("i = "+i)
          River.fire(EventCase("event-id-" + i, userId = None, id = "test-" + i))
      }

      1 must_== 1
    }
  }

}

class TestWatcher(app: play.api.Application) extends Plugin with Watcher {
  override def watch(implicit ec: ExecutionContext) = {
    case e => Enumerator(Watcher.Push(e, NotificationCase(e.id, "root", "topic")))
  }
}

class TestWrapper(app: play.api.Application) extends Plugin with Wrapper {
  override def apply(event: Event, notification: Notification)(implicit ec: ExecutionContext): Enumerator[Envelop] =
    Enumerator(
      Envelop.Instantly("play-logger", notification.toString),
      Envelop.DigestIfNotInstantly("play-logger", 20 millis),
      Envelop.Digest("play-logger", 10 millis)
    )
}

class TestDigestView(app: play.api.Application) extends Plugin with DigestView {
  override def apply(topic: PendingTopic, items: Seq[(Event, Notification)])(implicit ec: ExecutionContext): Option[Future[Any]] =
    Some(Future.successful("DIGEST: " + items))
}

class TestChannel(app: play.api.Application) extends Plugin with Channel {
  override def id: String = "play-logger"

  override def digest(implicit ec: ExecutionContext) = {
    case s: String =>
      play.api.Logger.info("digest --- "+s)
      Future.successful(true)
  }

  override def instant(implicit ec: ExecutionContext) = {
    case s: String =>
      play.api.Logger.debug("instant ========= "+s)
      Future.successful(true)
  }
}