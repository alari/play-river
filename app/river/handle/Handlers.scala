package river.handle

import river.data.Event

/**
 * @author alari
 * @since 4/7/14
 */
trait Handlers extends ((Event)=>Handler)
