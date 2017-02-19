package lacasa.akka.actor

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.reflect.runtime.universe._

import akka.actor.{Actor => AkkaActor, ActorContext, ActorRef => AkkaActorRef, PoisonPill, Terminated}
import akka.event.LoggingAdapter
import akka.util.Timeout

import lacasa.{Box, CanAccess, Safe, Packed, NoReturnControl}


private case class SafeWrapper[T](value: T) {
  implicit val safeEv: Safe[T] = implicitly
}

object Actor {
  implicit val poisonPillIsSafe: Safe[PoisonPill] = new Safe[PoisonPill] {}
  implicit val terminatedIsSafe: Safe[Terminated] = new Safe[Terminated] {}
}

trait Actor extends AkkaActor {
  import Actor._

  implicit def actorRefToSafeActorRef(actorRef: AkkaActorRef) = ActorRef(actorRef)

  // TODO: compiles even when commenting out the following implicit
  implicit val loggingIsSafe = new Safe[LoggingAdapter] {}

  protected final val safeSelf: ActorRef = self

  def receive(msg: Box[Any])(implicit acc: CanAccess { type C = msg.C }): Unit


  // In the event that we get an unrecognizable message, it can potentially
  // be a system message. E.g., `Terminated` (from context.watch) or `PoisonPill`.
  //
  // I haven't found a good way to generically support these types of messages yet.
  // They are therefore hardcoded here. It's not particularly pretty, nor robust.
  final override def receive: Receive = {
    case packed: Packed[_] =>
      receivePacked(packed)

    case x: SafeWrapper[_] =>
      implicit val valueIsSafe = x.safeEv
      receiveSafe(x.value)

    case x: PoisonPill =>
      receiveSystem(x)

    case x: Terminated =>
      receiveSystem(x)

    case x =>
      receiveUnknown(x)
  }

  private[actor] def receivePacked[T](packed: Packed[T]) = {
    try {
      receive(packed.box)(packed.access)
    } catch {
      case _: NoReturnControl => /* do nothing */
        Box.uncheckedCatchControl
    }
  }

  private[actor] def receiveSafe[T: Safe](msg: T) = {
    try {
      Box.mkBoxOf(msg) { packed =>
        receive(packed.box)(packed.access)
      }
    } catch {
      case _: NoReturnControl =>
        Box.uncheckedCatchControl
    }
  }

  private[actor] def receiveSystem[T: Safe](msg: T) = {
    receiveSafe(msg)
  }

  private[actor] def receiveUnknown(msg: Any) = {}
}

object ActorRef {

  implicit val safeActorRefIsSafe: Safe[ActorRef] = new Safe[ActorRef] {}

  def apply(ref: AkkaActorRef): ActorRef =
    new ActorRef(ref)

}

trait SafeReceive { self: Actor =>

  override def receiveSafe[T: Safe](msg: T) = {
    safeReceive(msg)
  }

  /**
   * This serves as a direct replacement to the standard Akka `receive` method.
   */
  def safeReceive: Receive

  /**
   * Override this method if you want to both receive Safe and boxed messages.
   */
  def receive(msg: Box[Any])(implicit acc: CanAccess { type C = msg.C }): Unit = {
    val contents = msg.extract(x => (if (x == null) "" else x).toString)
    throw new UnsupportedOperationException(s"Got unexpected Box($contents). Did you forget to mark it as Safe?")
  }
}

class ActorRef(private[actor] val ref: AkkaActorRef) {

  def !! [T](msg: Box[T])(implicit acc: CanAccess { type C = msg.C }): Nothing = {
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

  def forward[T: Safe](msg: T)(implicit context: ActorContext): Unit = {
    ref.forward(new SafeWrapper(msg))
  }

  def sendAndThen[T](msg: Box[T])(cont: () => Unit)(implicit acc: CanAccess { type C = msg.C }): Nothing = {
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
            (implicit timeout: Timeout, acc: CanAccess { type C = msg.C }): Nothing = {
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
    }
  }
}
