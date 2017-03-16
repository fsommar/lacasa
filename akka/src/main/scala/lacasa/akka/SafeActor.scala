package lacasa.akka.actor

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import scala.reflect.runtime.universe._

import akka.actor.{Actor => AkkaActor, ActorContext => AkkaActorContext, ActorRef => AkkaActorRef,
                   PoisonPill, Terminated, ActorCell, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.util.Timeout

import lacasa.{Box, Safe, Packed, NoReturnControl}


private[akka] case class SafeWrapper[T](value: T) {
  implicit val safeEv: Safe[T] = implicitly
}

object ActorRef {
  implicit def actorRefToSafeActorRef(actorRef: AkkaActorRef) = ActorRef(actorRef)
  implicit val safeActorRefIsSafe: Safe[ActorRef] = new Safe[ActorRef] {}

  def apply(ref: AkkaActorRef): ActorRef =
    new ActorRef(ref)
}

object Actor {
  implicit val poisonPillIsSafe: Safe[PoisonPill] = new Safe[PoisonPill] {}
  implicit val terminatedIsSafe: Safe[Terminated] = new Safe[Terminated] {}
}

object ActorContext {
  implicit def actorContextToSafeActorContext(actorContext: AkkaActorContext) = new ActorContext(actorContext)
  implicit def safeActorContextToActorContext(actorContext: ActorContext) = actorContext.unsafe
}

class ActorContext private (val unsafe: AkkaActorContext) {
  implicit def dispatcher: ExecutionContextExecutor = unsafe.dispatcher
  // implicit def system: ActorSystem = unsafe.system

  def become(behavior: AkkaActor.Receive, discardOld: Boolean): Unit = {
    unsafe.become(behavior, discardOld)
  }

  def child(name: String): Option[ActorRef] = {
    unsafe.child(name).map(ActorRef(_))
  }

  def children: immutable.Iterable[ActorRef] = {
    unsafe.children.map(ActorRef(_))
  }

  def parent: ActorRef = {
    unsafe.parent
  }

  def self: ActorRef = {
    unsafe.self
  }

  def sender(): ActorRef = {
    unsafe.sender()
  }

  def unwatch(subject: ActorRef): ActorRef = {
    unsafe.unwatch(subject.ref)
  }

  def watch(subject: ActorRef): ActorRef = {
    unsafe.watch(subject.ref)
  }

  def stop(actor: ActorRef): Unit = {
    unsafe.stop(actor.ref)
  }
}

trait Actor extends AkkaActor {
  import Actor._

  // TODO: compiles even when commenting out the following implicit
  implicit val loggingIsSafe = new Safe[LoggingAdapter] {}

  implicit val ctx: ActorContext = context
  final val safeSelf: ActorRef = ctx.self
  final def safeSender: ActorRef = ctx.sender

  def receive(msg: Box[Any])(implicit acc: msg.Access): Unit = {
    val contents = msg.extract(x => s"$x")
    throw new UnsupportedOperationException(s"Got unexpected Box($contents). Did you forget to mark it as Safe?")
  }

  /** AKKA INTERNAL API */
  override def aroundReceive(receive: Receive, msg: Any): Unit = {
    // In the event that we get an unrecognizable message, it can potentially
    // be a system message. E.g., `Terminated` (from context.watch) or `PoisonPill`.
    //
    // I haven't found a good way to generically support these types of messages yet.
    // They are therefore hardcoded here. It's not particularly pretty, nor robust.
    msg match {
      case packed: Packed[_] =>
        receivePacked(receive, packed)

      case x: SafeWrapper[_] =>
        implicit val ev = x.safeEv
        receiveSafe(receive, x.value)

      case x: PoisonPill =>
        receiveSystem(receive, x)

      case x: Terminated =>
        receiveSystem(receive, x)

      case x =>
        receiveUnknown(receive, x)
    }
  }

  private[actor] def receivePacked[T](_receive: Receive, packed: Packed[T]) = {
    Box.unsafe {
      receive(packed.box)(packed.access)
    }
  }

  private[actor] def receiveSafe[T: Safe](receive: Receive, msg: T) = {
    super.aroundReceive(receive, msg)
  }

  private[actor] def receiveSystem[T: Safe](receive: Receive, msg: T) = {
    receiveSafe(receive, msg)
  }

  private[actor] def receiveUnknown(_receive: Receive, msg: Any) = {
    throw new UnsupportedOperationException(s"Got unsupported message $msg."
      + " Did you accidentally send a message via an Akka ActorRef?")
  }
}

/**
 * By mixing in this trait, all Safe messages will be passed along
 * to the box receive method, along with boxed messages.
 */
trait OnlyBoxReceive { self: Actor =>

  private[actor] override def receiveSafe[T: Safe](_receive: Receive, msg: T) = {
    Box.unsafe {
      Box.mkBoxOf(msg) { packed =>
        receive(packed.box)(packed.access)
      }
    }
  }

  def receive: Receive = PartialFunction.empty

}

class ActorRef(private[akka] val ref: AkkaActorRef) {

  def !! [T](msg: Box[T])(implicit acc: msg.Access): Nothing = {
    // have to create a `Packed[T]`
    ref ! msg.pack()  // `pack()` accessible within package `lacasa`
    throw new NoReturnControl
  }

  /**
   * A simplification of the regular tell, which automatically creates a box,
   * provided that `msg` is valid to box, and sends it.
   *
   * In contrast to sending a box, this method doesn't need to capture the
   * value of the argument and it is therefore safe to continue normal
   * execution after the point of calling this method.
   */
  def ! [T: Safe](msg: T)(implicit sender: AkkaActorRef = AkkaActor.noSender): Unit = {
    ref ! new SafeWrapper(msg)
  }

  def tell[T: Safe](msg: T, sender: AkkaActorRef): Unit = {
    ref.tell(new SafeWrapper(msg), sender)
  }

  def forward[T: Safe](msg: T)(implicit context: AkkaActorContext): Unit = {
    ref.forward(new SafeWrapper(msg))(context)
  }

  def sendAndThen[T](msg: Box[T])(cont: () => Unit)(implicit acc: msg.Access): Nothing = {
    // have to create a `Packed[T]`
    ref ! msg.pack()  // `pack()` accessible within package `lacasa`
    cont()
    throw new NoReturnControl
  }

  /**
   * UNSTABLE. The ask pattern is a tough beast.
   *
   * In order to properly care for whenever SafeReceive is used, we need to
   * ensure that the message returned is either (a) Safe or (b) a box.
   * The tough part is that there actually is an alternative (c), which might happen
   * if the user (accidentally) sends a message through the regular Akka ActorRef,
   * without going through this wrapper.
   *
   * Therefore, one cannot automatically assume that any message not part of (b) --
   * which is easily determined by pattern matching for Packed[_] -- is part of (a).
   *
   * The better alternative to using the ask pattern is probably to do the conversation
   * explicit, by sending regular messages, as the current API for this method is clunky
   * and hard to use in an effective manner.
   */
  def ask[T](msg: Box[T])(cont: Future[Packed[Any]] => Unit)
            (implicit timeout: Timeout, acc: msg.Access): Nothing = {
    val future = akka.pattern.ask(ref, msg.pack())
    cont(future.mapTo[Packed[Any]])
    throw new NoReturnControl
  }

  def ask[T: Safe](msg: T)(implicit context: ActorContext, timeout: Timeout): Future[Any] = {
    import context.dispatcher
    val future = akka.pattern.ask(ref, new SafeWrapper(msg))
    // Non-safe messages are disallowed, and will cause runtime exceptions.
    future.map {
      case SafeWrapper(value) =>
        value
      case _: Packed[_] =>
        throw new UnsupportedOperationException("Expected Safe value return from ask, got boxed value."
          + " Did you mean to use `ask[T](msg: Box[T])`?")
      case x =>
        throw new UnsupportedOperationException(s"Expected Safe value returned from ask, got $x."
          + " Did you accidentally use `sender` instead of `safeSender`?")
    }
  }

  def ? [T: Safe](msg: T)(implicit context: ActorContext, timeout: Timeout): Future[Any] = {
    this.ask(msg)
  }
}
