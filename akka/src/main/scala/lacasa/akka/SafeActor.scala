package lacasa.akka

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

import akka.actor.{Actor, ActorRef}
import akka.event.LoggingAdapter
import akka.util.Timeout

import lacasa.{Box, CanAccess, Safe, Packed, NoReturnControl}

private case object LaCasaInitMessage

trait SafeActor[T] extends Actor {

  implicit def actorRefToSafeActorRef(actorRef: ActorRef) = SafeActorRef[T](actorRef)

  // TODO: compiles even when commenting out the following implicit
  implicit val loggingIsSafe = new Safe[LoggingAdapter] {}

  protected final val safeSelf: SafeActorRef[T] = self

  def receive(msg: Box[T])(implicit acc: CanAccess { type C = msg.C }): Unit
  def init(): Unit = {}

  final def receive = {
    case LaCasaInitMessage =>
      try {
        init()
      } catch {
        case _: NoReturnControl =>
          Box.uncheckedCatchControl
      }

    case packed: Packed[T] =>
      try {
        receive(packed.box)(packed.access)
      } catch {
        case nrc: NoReturnControl => /* do nothing */
          Box.uncheckedCatchControl
      }

    case _ => // internal error
  }

}

object SafeActorRef {

  implicit def safeActorRefIsSafe[T]: Safe[SafeActorRef[T]] = new Safe[SafeActorRef[T]] {}

  def apply[T](ref: ActorRef): SafeActorRef[T] =
    new SafeActorRef[T](ref)

  def init[T](ref: SafeActorRef[T]): Unit = {
    ref.ref ! LaCasaInitMessage
  }

}

class SafeActorRef[T](private val ref: ActorRef) {

  def ! (msg: Box[T])(implicit acc: CanAccess { type C = msg.C }): Nothing = {
    // have to create a `Packed[T]`
    ref ! msg.pack()  // `pack()` accessible within package `lacasa`
    throw new NoReturnControl
  }

  /**
   * A simplification of the regular send, which automatically creates a box,
   * provided that `msg` is valid to box, and sends it.
   */
  def ! (msg: T)(implicit ev: Safe[T]): Nothing = {
    Box.mkBoxOf(msg) { packed =>
      implicit val acc = packed.access
      this ! packed.box
    }
  }

  def sendAndThen(msg: Box[T])(cont: () => Unit)(implicit acc: CanAccess { type C = msg.C }): Nothing = {
    // have to create a `Packed[T]`
    ref ! msg.pack()  // `pack()` accessible within package `lacasa`
    cont()
    throw new NoReturnControl
  }

  def ask(msg: Box[T])(cont: Future[T] => Unit)
         (implicit timeout: Timeout, ec: ExecutionContext,
          ev: Safe[T], acc: CanAccess { type C = msg.C }): Nothing = {
    // The cast should be okay as the receiver (this), only can receive messages of type `T`;
    // the `Future` contains a message of type `T` at runtime.
    val future = akka.pattern.ask(ref, msg.pack()).map { case packed: Packed[T] =>
      implicit val acc = packed.access
      packed.box.extract(identity)
    }
    cont(future)
    throw new NoReturnControl
  }
}
