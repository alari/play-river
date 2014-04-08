package mirari.river

import play.api.libs.iteratee.{Concurrent, Iteratee, Enumerator, Enumeratee}
import mirari.river.data.{Notification, Event}
import mirari.river.channel.Channel
import scala.concurrent.{Future, ExecutionContext}
import org.joda.time.DateTime

/**
 * @author alari
 * @since 4/8/14
 */
trait River {
  def source: Enumerator[Event]

  def checkDelayedSource: Enumerator[River.CheckDelayed.type]

  def logger: RiverLogger

  def watchers: Seq[Watcher]

  def watch(implicit ec: ExecutionContext): Enumeratee[Event, Watcher.Action] =
    Enumeratee
      .mapFlatten[Event](e =>
      watchers
        .foldLeft(Enumerator.empty[Watcher.Action]) {
        case (a, b) => Enumerator.interleave(a, b(e))
      })

  def storage: NotificationStorage

  def channels: Seq[Channel]

  def channel(implicit ec: ExecutionContext): Enumeratee[String, Channel] =
    Enumeratee.filter[String](id => channels.exists(_.id == id)) ><> Enumeratee.map[String](id => channels.find(_.id == id).get)

  def wrappers: Seq[Wrapper]

  def wrap(implicit ec: ExecutionContext): Enumeratee[(Event, Notification), (Notification, Seq[Envelop])] =
    Enumeratee
      .mapFlatten[(Event, Notification)] {
      case (e, n) =>
        Enumerator.generateM(wrappers
          .foldLeft(Enumerator.empty[Envelop]) {
          case (a, b) => Enumerator.interleave(a, b(e, n))
        } |>>> Iteratee.getChunks map (es => Some(n -> es)))

    }

  def instants(implicit ec: ExecutionContext): Enumeratee[(Notification, Seq[Envelop]), (Notification, Seq[Envelop.Delay])] =
    Enumeratee.mapFlatten {
      case (notification, envelops) =>
        val instantly = envelops.filter {
          case _: Envelop.Instantly[_] => true
          case _ => false
        }.asInstanceOf[Seq[Envelop.Instantly[_]]]

        Enumerator generateM Future
          .sequence(
            instantly
              .map(e =>
              channels.find(_.id == e.channelId)
                .map(_.instant(e.view))
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
        }.map(sq => Some(notification -> sq))
    }

  def delay(implicit ec: ExecutionContext): Iteratee[(Notification, Seq[Envelop.Delay]), Unit] =
    Enumeratee.filter[(Notification, Seq[Envelop.Delay])](_._2.nonEmpty) ><>
      Enumeratee.map[(Notification, Seq[Envelop.Delay])] {
        case (n, Seq()) =>
          n -> Seq.empty[(String, DateTime)]
        case (n, es) =>
          n -> es.map(e => e.channelId -> DateTime.now().plusMinutes(e.delay.toMinutes.toInt))
      } &>> storage.delay

  def flow(src: Enumerator[Event])(implicit ec: ExecutionContext) = src &> logger.insert ><> watch ><> storage.act ><> wrap ><> instants |>> delay

  def fireSingle(e: Event)(implicit ec: ExecutionContext) = flow(Enumerator(e))

  def fire(e: Event): Unit

  def pendings(implicit ec: ExecutionContext): Enumeratee[River.CheckDelayed.type, PendingTopic] = Enumeratee.mapFlatten(_ => storage.pendings)

  def zipWithEvents(implicit ec: ExecutionContext): Enumeratee[List[Notification], List[(Event, Notification)]] = Enumeratee.mapM[List[Notification]] {
    ns =>
      Enumerator(ns.map(_.eventId).asInstanceOf[TraversableOnce[String]]) &> logger.getByIds ><> Enumeratee.map[Event] {
        e =>
          e -> ns.find(_.eventId == e.id).get
      } |>>> Iteratee.getChunks
  }

  def topicWithEvents(implicit ec: ExecutionContext): Enumeratee[(PendingTopic,List[Notification]),(PendingTopic,List[(Event,Notification)])] =
    Enumeratee.mapFlatten[(PendingTopic,List[Notification])] {
      case (t,ns) =>
        Enumerator(ns) &> zipWithEvents ><> Enumeratee.map(es => t -> es)
    }

  def digestViews: Seq[DigestView]

  def digestView(implicit ec: ExecutionContext): Enumeratee[(PendingTopic,List[(Event,Notification)]),(PendingTopic,Any)] = Enumeratee.mapFlatten{
    case (t, is) =>
      Enumerator.flatten(
        Future sequence digestViews.flatMap(_(t, is)) map {vs => vs.map(v => t -> v)} map {sq => Enumerator.enumerate(sq)}
      )
  }

  def sendDigest(implicit ec: ExecutionContext): Enumeratee[(PendingTopic,Any), Boolean] = Enumeratee.mapM {
    case (t,v) =>
      channels.find(_.id == t.channelId).map(_.digest(v)).getOrElse(Future.successful(false))
  }

  def digest(src: Enumerator[River.CheckDelayed.type])(implicit ec: ExecutionContext) =
    src &> pendings ><> storage.pendingTopicNotifications ><> topicWithEvents ><> digestView ><> sendDigest |>> Iteratee.ignore

  def run()(implicit ec: ExecutionContext) {
    digest(checkDelayedSource)
    flow(source)
  }

}

object River extends River{
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

  override def checkDelayedSource: Enumerator[CheckDelayed.type] = {
    import scala.concurrent.duration._
    import play.api.libs.concurrent.Execution.Implicits.defaultContext
    val (cdsource, cdsender) = Concurrent.broadcast[CheckDelayed.type ]
    play.api.libs.concurrent.Akka.system(play.api.Play.current).scheduler.schedule(100 millis, 500 millis){
      cdsender.push(CheckDelayed)
    }
    cdsource
  }

  import play.api.libs.concurrent.Execution.Implicits.defaultContext
  run()
}