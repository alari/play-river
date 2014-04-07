package river

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.test._
import org.joda.time.DateTime
import java.util.UUID
import river.data.Event
import river.inmemory.{EventCase, MemoryRiver}

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  "river" should {

    "work" in new WithApplication{
      (1 to 10).foreach { i=>
        MemoryRiver.fire(EventCase("test "+i))
      }

      1 must_== 1
    }
  }
}
