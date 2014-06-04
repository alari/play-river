package infra.river

/**
 * @author alari
 * @since 6/4/14
 */
case class Grouping(
                     eventId: Boolean = false,
                     contexts: Seq[String] = Seq.empty,
                     userId: Boolean = false,
                     topic: Boolean = false
                     )