package mirari.river

import play.api.test.FakeApplication

/**
 * @author alari
 * @since 4/10/14
 */
package object mongo {
  def fakeApp = FakeApplication(
  additionalPlugins = Seq("mirari.river.mongo.EventDAO", "mirari.river.mongo.NotificationDAO", "play.modules.reactivemongo.ReactiveMongoPlugin"),
  additionalConfiguration = Map(
    "mongodb.db" -> "play-river-mongo"
  )
  )
}
