package mirari.river

import play.api.libs.iteratee.{Concurrent, Iteratee, Enumerator, Enumeratee}
import mirari.river.data.{EventCase, Notification, Event}
import scala.concurrent.{Future, ExecutionContext}
import org.joda.time.DateTime
import ch.qos.logback.core.util.ExecutorServiceUtil

/**
 * @author alari
 * @since 4/8/14
 */
trait River {
  /**
   * Source of events to flow
   * @return
   */
  def source: Enumerator[Event]

  /**
   * Source of digest triggers
   * @return
   */
  def checkDelayedSource: Enumerator[River.CheckDelayed.type]

  /**
   * Logger -- event storage
   * @return
   */
  def logger: RiverLogger

  /**
   * Event watchers -- produces reactions for some events
   * @return
   */
  def watchers: Seq[Watcher]

  /**
   * Stream of actions by events
   * Find out there who are to be notified, how many notifications do you need, etc
   * @param ec
   * @return
   */
  def watch(implicit ec: ExecutionContext): Enumeratee[Event, Watcher.Action] =
    Enumeratee
      .mapFlatten[Event](e =>
      watchers
        .map(_.watch.lift(e))
        .filter(_.isDefined)
        .map(_.get)
        .foldLeft(Enumerator.empty[Watcher.Action])(_ interleave _)
      )

  /**
   * Notifications storage
   * @return
   */
  def storage: NotificationStorage

  /**
   * Channels -- transport for either instant or delayed messages, or both
   * @return
   */
  def channels: Seq[Channel]

  /**
   * A channel's stream -- returns a channel for its id
   * @param ec
   * @return
   */
  def channel(implicit ec: ExecutionContext): Enumeratee[String, Channel] =
    Enumeratee.filter[String](id => channels.exists(_.id == id)) ><> Enumeratee.map[String](id => channels.find(_.id == id).get)

  /**
   * Wrappers -- wraps an event and notification with an event envelops
   * Find out there, to which channels would you like to send notification, should it be delayed or not
   * @return
   */
  def wrappers: Seq[Wrapper]

  /**
   * Generates an envelopes stream for an event and notification pair by wrapping them
   * @param ec
   * @return
   */
  def wrap(implicit ec: ExecutionContext): Enumeratee[(Event, Notification), (Notification, Seq[Envelop])] =
    Enumeratee
      .mapFlatten[(Event, Notification)] {
      en =>
        Enumerator.flatten(wrappers
          .map(_.wrap.lift(en))
          .filter(_.isDefined)
          .map(_.get)
          .foldLeft(Enumerator.empty[Envelop])(_ interleave _) |>>> Iteratee.getChunks map (es => en._2 -> es) map (Enumerator(_)))
    }

  /**
   * Delivers all instant envelops, returns others to be delayed
   * @param ec
   * @return
   */
  def instants(implicit ec: ExecutionContext): Enumeratee[(Notification, Seq[Envelop]), (Notification, Seq[Envelop.Delay])] =
    Enumeratee.mapFlatten {
      case (notification, envelops) =>
        val instantly = envelops.filter {
          case _: Envelop.Instantly[_] => true
          case _ => false
        }.asInstanceOf[Seq[Envelop.Instantly[_]]]

        Enumerator flatten Future
          .sequence(
            instantly
              .map(e =>
              channels.find(_.id == e.channelId)
                .map(_.instant.applyOrElse(e.view, (_: Any) => Future.successful(false)))
              ).filter(_.isDefined)
              .map(_.get)).map(_.exists(b => b))
          .map {
          case true =>
            envelops.filter {
              case _: Envelop.Digest => true
              case _ => false
            }.asInstanceOf[Seq[Envelop.Delay]]
          case false =>
            envelops.filter {
              case _: Envelop.Delay => true
              case _ => false
            }.asInstanceOf[Seq[Envelop.Delay]]
        }.map(sq => Enumerator(notification -> sq))
    }

  /**
   * Delays notification in some channels for a given period
   * @param ec
   * @return
   */
  def delay(implicit ec: ExecutionContext): Iteratee[(Notification, Seq[Envelop.Delay]), Unit] =
    Enumeratee.filter[(Notification, Seq[Envelop.Delay])](_._2.nonEmpty) ><>
      Enumeratee.map[(Notification, Seq[Envelop.Delay])] {
        case (n, Seq()) =>
          n -> Seq.empty[(String, DateTime)]
        case (n, es) =>
          n -> es.map(e => e.channelId -> DateTime.now().plusSeconds(e.delay.toSeconds.toInt))
      } &>> storage.delay

  /**
   * Runs a notifications flow for a source of events
   * @param src
   * @param ec
   * @return
   */
  def flow(src: Enumerator[Event])(implicit ec: ExecutionContext) =
    src &> buffer[Event] ><>
      logger.insert ><> buffer ><>
      watch ><> buffer ><>
      storage.act ><> buffer ><>
      wrap ><> buffer ><>
      instants ><> buffer |>> delay

  /**
   * Just fire a single event with a special created flow
   * @param e
   * @param ec
   * @return
   */
  def fireSingle(e: Event)(implicit ec: ExecutionContext) = flow(Enumerator(e))

  /**
   * Fire a single event
   * @param e
   */
  def fire(e: Event): Unit

  /**
   * Stream of pending topics. Every topic represents a single digest for a single channel, possibly containing several items inside
   * @param ec
   * @return
   */
  def pendings(implicit ec: ExecutionContext): Enumeratee[River.CheckDelayed.type, PendingTopic] = Enumeratee.mapFlatten(_ => storage.pendings)

  /**
   * Links notifications with appropriate events
   * @param ec
   * @return
   */
  def zipWithEvents(implicit ec: ExecutionContext): Enumeratee[List[Notification], List[(Event, Notification)]] = Enumeratee.mapM[List[Notification]] {
    ns =>
      Enumerator(ns.map(_.eventId).asInstanceOf[TraversableOnce[String]]) &> logger.getByIds ><> Enumeratee.map[Event] {
        e =>
          e -> ns.find(_.eventId == e.id).get
      } |>>> Iteratee.getChunks
  }

  /**
   * Returns events for topic and notifications
   * @param ec
   * @return
   */
  def topicWithEvents(implicit ec: ExecutionContext): Enumeratee[(PendingTopic, List[Notification]), (PendingTopic, List[(Event, Notification)])] =
    Enumeratee.mapFlatten[(PendingTopic, List[Notification])] {
      case (t, ns) =>
        Enumerator(ns) &> zipWithEvents ><> Enumeratee.map(es => t -> es)
    }

  /**
   * Digest view wraps a pending topic with all appropriate data into a view suitable for concrete channel
   * @return
   */
  def digestViews: Seq[DigestView]

  /**
   * Wraps a topic into a view for a digest
   * @param ec
   * @return
   */
  def digestView(implicit ec: ExecutionContext): Enumeratee[(PendingTopic, List[(Event, Notification)]), (PendingTopic, Any)] = Enumeratee.mapFlatten {
    case (t, is) =>
      Enumerator.flatten(
        Future.sequence(digestViews.map(_.toDigest.lift(t, is))
          .filter(_.isDefined)
          .map(_.get)).map(_.map(v => t -> v)).map(Enumerator.enumerate(_))
      )
  }

  /**
   * Sends a digest, returns topics sent
   * @param ec
   * @return
   */
  def sendDigest(implicit ec: ExecutionContext): Enumeratee[(PendingTopic, Any), PendingTopic] = Enumeratee.mapM {
    case (t, v) =>
      channels.find(_.id == t.channelId).map(_.digest.applyOrElse(v, (_: Any) => Future.successful(false)).map(_ => t)).getOrElse(Future.successful(t))
  }

  /**
   * Digest flow
   * @param src
   * @param ec
   * @return
   */
  def digest(src: Enumerator[River.CheckDelayed.type])(implicit ec: ExecutionContext) =
    src &> pendings ><>
      buffer ><> storage.pendingTopicNotifications ><>
      buffer ><> topicWithEvents ><>
      buffer ><> digestView ><>
      buffer ><> sendDigest ><>
      buffer ><> storage.pendingProcessed |>> Iteratee.ignore

  /**
   * Runs all flows for default sources
   * @param ec
   * @return
   */
  def run()(implicit ec: ExecutionContext) {
    digest(checkDelayedSource)
    flow(source)
  }

  private def debug[T](m: String)(implicit ec: ExecutionContext) = Enumeratee.map[T] {
    o =>
      play.api.Logger.debug(s"debug($m): $o")
      o
  }

  def buffer[T] = Concurrent.buffer[T](200)

}

object River extends River {

  case object CheckDelayed

  override def digestViews: Seq[DigestView] = play.api.Play.current.plugins.filter {
    case _: DigestView => true
    case _ => false
  }.asInstanceOf[Seq[DigestView]]

  override def wrappers: Seq[Wrapper] = play.api.Play.current.plugins.filter {
    case _: Wrapper => true
    case _ => false
  }.asInstanceOf[Seq[Wrapper]]

  override def watchers: Seq[Watcher] = play.api.Play.current.plugins.filter {
    case _: Watcher => true
    case _ => false
  }.asInstanceOf[Seq[Watcher]]

  override def channels: Seq[Channel] = play.api.Play.current.plugins.filter {
    case _: Channel => true
    case _ => false
  }.asInstanceOf[Seq[Channel]]

  override def logger: RiverLogger = play.api.Play.current.plugin[RiverLogger].getOrElse(RiverLogger)

  override def storage: NotificationStorage = play.api.Play.current.plugin[NotificationStorage].getOrElse(NotificationStorage)

  val (source, sender) = Concurrent.broadcast[Event]

  override def fire(e: Event): Unit = sender.push(e)

  def fire(action: String,
           userId: Option[String],

           contexts: Map[String, String] = Map.empty,
           artifacts: Map[String, String] = Map.empty,

           timestamp: DateTime = DateTime.now()): Unit = fire(EventCase(
    action, userId, contexts, artifacts, timestamp
  ))

  private implicit val ec = ExecutionContext.fromExecutorService(ExecutorServiceUtil.newExecutorService())

  override def checkDelayedSource: Enumerator[CheckDelayed.type] = {
    import scala.concurrent.duration._
    val (cdsource, cdsender) = Concurrent.broadcast[CheckDelayed.type]
    val period = play.api.Play.current.configuration.getMilliseconds("river.period").map(_ millis).getOrElse(1 minute)
    play.api.libs.concurrent.Akka.system(play.api.Play.current).scheduler.schedule(period, period) {
      cdsender.push(CheckDelayed)
    }
    cdsource
  }

  run()
}