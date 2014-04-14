package infra.river.mongo

import org.joda.time.DateTime

/**
 * @author alari
 * @since 4/14/14
 */
case class NotificationPending(channelId: String, delayedTill: DateTime)
