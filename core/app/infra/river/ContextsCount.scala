package infra.river

import infra.river.data.Contexts

/**
 * @author alari
 * @since 4/22/14
 */
case class ContextsCount(contexts: Map[String,String], count: Long) extends Contexts