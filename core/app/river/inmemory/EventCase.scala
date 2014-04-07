package river.inmemory

import river.data.Event
import java.util.UUID
import org.joda.time.DateTime

/**
 * @author alari
 * @since 4/7/14
 */
case class EventCase(


                      action: String,
                      userId: Option[String] = None,

                      contexts: Map[String, String] = Map.empty,
                      artifacts: Map[String, String] = Map.empty,

                      id: String = UUID.randomUUID().toString,

                      timestamp: DateTime = DateTime.now()
                      ) extends Event
