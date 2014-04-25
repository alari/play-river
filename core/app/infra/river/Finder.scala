package infra.river

/**
 * @author alari
 * @since 4/8/14
 */
case class Finder(
                   userId: Option[String] = None,
                   contexts: Option[Map[String, String]] = None,
                   viewed: Option[Boolean] = None,
                   topic: Option[String] = None,
                   digestChannel: Option[String] = None
                   )