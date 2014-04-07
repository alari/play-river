package river

import river.data.Event

/**
 * @author alari
 * @since 4/7/14
 */
case class Envelop(event: Event, handler: Handler)
