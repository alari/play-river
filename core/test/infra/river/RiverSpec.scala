package infra.river

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.test.{FakeApplication, WithApplication}
import play.api.Plugin
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee.Enumerator
import scala.concurrent.duration._
import infra.river.data.EventCase

/**
 * @author alari
 * @since 4/8/14
 */
@RunWith(classOf[JUnitRunner])
class RiverSpec extends Specification {
  "river" should {
    "work" in new WithApplication(FakeApplication(additionalPlugins = Seq(
      "infra.river.TestWatcher",
      "infra.river.TestWrapper",
      "infra.river.TestDigestView",
      "infra.river.TestChannel"
    ))) {
      (1 to 4000).foreach {
        i =>
        if(i % 100 == 0) {
          play.api.Logger.warn("i = "+i)
        }
          River.fire(EventCase("event-id-" + i, userId = None, id = "test-" + i))
      }

      1 must_== 1
    }
  }

}

class TestWatcher(app: play.api.Application) extends Plugin with Watcher {
  override def watch(implicit ec: ExecutionContext) = {
    case e => Enumerator(
      e.transient("root", "topic")
    )
  }
}

class TestWrapper(app: play.api.Application) extends Plugin with Wrapper {
  override def wrap(implicit ec: ExecutionContext) = {
    case (e, n) =>
      Enumerator(
        Envelop.Instantly("play-logger", n.toString),
        Envelop.DigestIfNotInstantly("play-logger", 20 millis),
        Envelop.Digest("play-logger", 10 millis)
      )
  }

}

class TestDigestView(app: play.api.Application) extends Plugin with DigestView {
  override def toDigest(implicit ec: ExecutionContext) = {
    case (t, is) =>
      Future.successful("DIGEST: " + is)
  }
}

class TestChannel(app: play.api.Application) extends Plugin with Channel {
  override def id: String = "play-logger"

  override def digest(implicit ec: ExecutionContext) = {
    case s: String =>
      play.api.Logger.info("digest --- " + s)
      Future.successful(true)
  }

  override def instant(implicit ec: ExecutionContext) = {
    case s: String =>
      play.api.Logger.debug("instant ========= " + s)
      Future.successful(true)
  }
}