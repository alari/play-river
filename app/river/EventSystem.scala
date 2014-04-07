package river


/**
 * @author alari
 * @since 4/7/14
 */
trait EventSystem {
  def fire: FireEvent

  def logger: EventLogger

  def wrapper: Wrapper

  def store: NotificationStore

  def channels: Seq[Channel]
}