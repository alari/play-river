package river

import river.data.Event

/**
 * @author alari
 * @since 4/7/14
 */
trait Wrapper extends ((Event)=>Handler)
