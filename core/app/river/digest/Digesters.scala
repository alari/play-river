package river.digest

/**
 * @author alari
 * @since 4/7/14
 */
trait Digesters extends ((Option[String])=>Digester)