package mirari.river.data

import org.joda.time.DateTime

/**
 * @author alari
 * @since 4/9/14
 */
case class EventCase(
                      action: String,
                      userId: Option[String],

                      contexts: Map[String, String] = Map.empty,
                      artifacts: Map[String, String] = Map.empty,

                      timestamp: DateTime = DateTime.now(),

                      id: String = null
                      ) extends Event