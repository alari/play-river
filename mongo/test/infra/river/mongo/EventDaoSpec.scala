package infra.river.mongo

import play.api.test.{WithApplication, PlaySpecification}
import infra.river.data.Event
import play.api.libs.iteratee.{Iteratee, Enumerator}
import scala.concurrent.duration._
import infra.river.data.EventCase
import scala.Some
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

/**
 * @author alari
 * @since 4/10/14
 */
class EventDaoSpec extends PlaySpecification {
  "event dao" should {
    "insert and return events" in new WithApplication(fakeApp) {
      app.plugin[EventDAO].isDefined must beTrue

      val dao = app.plugin[EventDAO].get

      val e: Event = EventCase("test", None)

      var se: Event = _

      Enumerator(e) &> dao.insert |>>> Iteratee.head must beLike[Option[Event]]{
        case Some(stored) =>
        se = stored
        se.action must_== "test"
        se.id must beNull.not
      }.await(1, 1 second)

      Enumerator(se.id) &> dao.getById |>>> Iteratee.head must beLike[Option[Event]] {
        case Some(s) =>
          s.id must_== se.id
      }.await(1, 1 second)

      (Enumerator(e) &> dao.insert |>>> Iteratee.head) must beLike[Option[Event]]{
        case Some(s) =>
        s.id must_!= se.id
        Enumerator[TraversableOnce[String]](Seq(se.id, s.id)) &> dao.getByIds |>>> Iteratee.getChunks must beLike[List[Event]]{
          case l @ List(s1, s2) =>
            l.exists(_.id == s.id) must beTrue
            l.exists(_.id == se.id) must beTrue
        }.await(1, 500 millis)
      }.await(1, 1 second)
    }
  }
}
