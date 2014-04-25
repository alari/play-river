package infra.river.mongo

import play.api.test.{WithApplication, PlaySpecification}
import infra.river.data.{Notification, Event}
import play.api.libs.iteratee.{Enumeratee, Iteratee, Enumerator}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import java.util.UUID
import org.joda.time.DateTime
import infra.river.{ContextsCount, Finder, PendingTopic}
import infra.river.data.EventCase

/**
 * @author alari
 * @since 4/10/14
 */
class NotificationDAOSpec extends PlaySpecification {
  "notification dao" should {
    "store notifications" in new WithApplication(fakeApp) {
      app.plugin[NotificationDAO].isDefined must beTrue
      app.plugin[EventDAO].isDefined must beTrue

      val eDao = app.plugin[EventDAO].get
      val dao = app.plugin[NotificationDAO].get

      val e: Event = EventCase("test2", None)
      val e2: Event = EventCase("test2", None)

      var se: Event = _
      var se2: Event = _

      Enumerator(e, e2) &> eDao.insert |>>> Iteratee.getChunks must beLike[List[Event]] {
        case List(s1, s2) =>
          se = s1
          se2 = s2
          se2.id must_!= se.id
      }.await(1, 1 second)

      val u1 = "1:" + UUID.randomUUID().toString
      val u2 = "2:" + UUID.randomUUID().toString
      val t = UUID.randomUUID().toString

      val ns = Seq[Notification](
        se.notification(u1, t),
        se.notification(u2, t),
        se2.notification(u2, t)
      )

      var sn: Seq[Notification] = _

      Enumerator.enumerate(ns) &> Enumeratee.map[Notification](n => {
        play.api.Logger.debug("going to insert " + n)
        n
      }) ><> dao.insert |>>> Iteratee.getChunks must beLike[List[Notification]] {
        case l@List(n1, n2, n3) =>
          sn = l
          l.count(_.eventId == se.id) must_== 2
          l.count(_.eventId == se2.id) must_== 1
          l.exists(_.userId == u1) must beTrue
          l.exists(_.userId == u2) must beTrue
      }.await(1, 1 second)


      dao.count(Finder(topic = Some(t))) must beEqualTo(2).await
      dao.count(Finder(userId = Some(u1))) must beEqualTo(1).await
      dao.count(Finder(userId = Some(u1), topic = Some(u1))) must beEqualTo(0).await
      dao.count(Finder(userId = Some(u1), topic = Some(t))) must beEqualTo(1).await

      val delayUnit = Enumerator(
        (sn(0), Seq(u1 -> DateTime.now().minusDays(1))),
        (sn(1), Seq(u1 -> DateTime.now().plusDays(1), u2 -> DateTime.now().minusDays(2))),
        (sn(2), Seq(u1 -> DateTime.now().minusDays(1)))
      ) |>>> dao.scheduleDigest

      delayUnit.map(_ => true).recover {
        case er: Throwable =>
          play.api.Logger.error("delay not delays", er)
          false
      } must beTrue.await(1, 1 second)

      dao.pendings |>>> Iteratee.getChunks must beLike[List[PendingTopic]] {
        case l =>
          play.api.Logger.debug("so: "+l)
          l.count(_.channelId == u1) must_== 2
          l.count(_.channelId == u2) must_== 1
          Enumerator.enumerate(l) &> dao.pendingProcessed |>>> Iteratee.getChunks must beLike[List[Boolean]] {
            case ll =>
              play.api.Logger.debug(ll.toString())
              ll.forall(b => b) must beTrue
          }.await(1, 300 millis)

          dao.pendings |>>> Iteratee.getChunks must beEqualTo(Nil).await(1, 300 millis)

      }.await(1, 1 second)
    }

    "count with groupings" in new WithApplication(fakeApp) {
      val eDao = app.plugin[EventDAO].get
      val dao = app.plugin[NotificationDAO].get

      val u1 = "ctx1:" + UUID.randomUUID().toString
      val u2 = "ctx2:" + UUID.randomUUID().toString
      val t = UUID.randomUUID().toString

      val e: Event = EventCase("test2", None, Map("test_1" -> u1))
      val e2: Event = EventCase("test2", None, Map("test_2" -> u2, "test_1" -> u1))

      var se: Event = _
      var se2: Event = _

      Enumerator(e, e2) &> eDao.insert |>>> Iteratee.getChunks must beLike[List[Event]] {
        case List(s1, s2) =>
          se = s1
          se2 = s2
          se2.id must_!= se.id
      }.await(1, 1 second)

      val ns = Seq[Notification](
        se.notification(u1, t),
        se.notification(u2, t),
        se2.notification(u2, t)
      )

      var sn: Seq[Notification] = _

      Enumerator.enumerate(ns) &> Enumeratee.map[Notification](n => {
        play.api.Logger.debug("going to insert " + n)
        n
      }) ><> dao.insert |>>> Iteratee.getChunks must beLike[List[Notification]] {
        case l@List(n1, n2, n3) =>
          sn = l
          l.count(_.eventId == se.id) must_== 2
          l.count(_.eventId == se2.id) must_== 1
          l.exists(_.userId == u1) must beTrue
          l.exists(_.userId == u2) must beTrue
      }.await(1, 1 second)

      dao.countContexts(Finder(viewed = Some(false), topic = Some(t)), "test_1") |>>> Iteratee.getChunks must beLike[List[ContextsCount]] {
        case List(a) =>
          a.contexts must_== Map("test_1" -> u1)
          a.count must_== 2
      }.await(1, 300 millis)
    }
  }
}
