package infra.river.mongo

import infra.river.data.Notification
import infra.mongo.MongoDomain
import org.joda.time.DateTime

/**
 * @author alari
 * @since 4/14/14
 */
case class NotificationDomain(
                               eventId: String,
                               userId: String,
                               topic: String,
                               timestamp: DateTime,
                               viewed: Boolean,
                               pending: Seq[NotificationPending],
                               contexts: Map[String, String],
                               _id: MongoDomain.Oid.Id
                               ) extends MongoDomain.Oid with Notification {
  lazy val digest = pending.map(nd => nd.channelId -> nd.delayedTill).toMap
}
