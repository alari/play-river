package infra.river

import play.api.test.FakeApplication

/**
 * @author alari
 * @since 4/10/14
 */
package object mongo {
  def fakeApp = FakeApplication(
  additionalPlugins = Seq("infra.river.mongo.EventDAO", "infra.river.mongo.NotificationDAO", "play.modules.reactivemongo.ReactiveMongoPlugin"),
  additionalConfiguration = Map(
    "mongodb.db" -> "play-river-mongo"
  )
  )
}
