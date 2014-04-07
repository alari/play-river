package river

/**
 * @author alari
 * @since 4/7/14
 */
case class NotificationsFinder(
                                userId: Option[String],
                                contexts: Option[Map[String, String]],
                                read: Option[Boolean],
                                topic: Option[String]
                                )