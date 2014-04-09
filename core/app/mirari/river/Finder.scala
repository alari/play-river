package mirari.river

/**
 * @author alari
 * @since 4/8/14
 */
case class Finder(
                   userId: Option[String] = None,
                   contexts: Option[Map[String, String]] = None,
                   read: Option[Boolean] = None,
                   topic: Option[String] = None,
                   delayed: Option[String] = None
                   )