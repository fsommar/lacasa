package akka.lacasa.actor

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import scala.reflect.runtime.universe._

import akka.actor.{Actor => AkkaActor, ActorContext => AkkaActorContext, ActorRef => AkkaActorRef,
                   ActorPath, RootActorPath, Address, Terminated, ActorInitializationException}
import akka.event.LoggingAdapter
import akka.util.Timeout

import lacasa.{Box, Packed, Safe}


object Actor {
  implicit val actorLogSource: akka.event.LogSource[Actor] = new akka.event.LogSource[Actor] {
    def genString(a: Actor) = a.self.path.toString
  }
}

trait Actor {

  def receive[T: Safe](msg: T): Unit

  // def receiveSystem(msg: SystemMessage): Unit

  // Since the ActorAdapter already has initialized and set the head of the contextStack to null,
  // that's what's being matched for, and any other structure of the contextStack is an exception.
  implicit val context: ActorContext =
    akka.actor.ActorCell.contextStack.get match {
      case null :: ctx :: _ => new ActorContextAdapter(ctx)
      case lst => throw ActorInitializationException(
        s"$lst\nYou cannot create an instance of [${getClass.getName}] explicitly using the constructor (new). " +
          "You have to use one of the 'actorOf' factory methods to create a new actor. See the documentation.")
    }
 
  implicit final val self: ActorRef = context.self

   // TODO: SafeActorRef
  final def sender(): ActorRef = context.sender()
}

private[akka] case class SafeWrapper[T](value: T) {
  implicit val safeEv: Safe[T] = implicitly
}

private class ActorAdapter(_actor: => Actor) extends AkkaActor {
  val ref = _actor

  def receive = running

  def running: Receive = {
    case packed: Packed[_] =>
      ???

    case x: SafeWrapper[_] =>
      implicit val ev = x.safeEv
      ref.receive(x.value)

    case x =>
      ???
  }

  protected def start(): Unit = {
    context.become(running)
  }
}

trait ActorLogging { this: Actor ⇒
  private var _log: LoggingAdapter = _

  def log: LoggingAdapter = {
    if (_log eq null)
      _log = akka.event.Logging(context.system.asInstanceOf[ActorSystemAdapter].untyped, this)
    _log
  }

}


/*
TODO: Split tell[T: Safe](T) and tell(Box[Any]) into two traits, for both Actor and ActorRef.
That way, an ActorRef to an Actor that only supports receiving safe messages, will only allow
sending those things. Feedback will be given at compile time, instead of keeping
track of it at runtime (by throwing exceptions).

If done correctly, it should allow for a very simple migration path from an existing Akka
application, given that 1) it uses the allowed subset of functionality and 2) it only sends
Safe types. However, if the user has objects that need to be send in boxes, then they can do
too, knowing that it will require more effort to migrate.

TODO: Make ActorAdapter work for the aforementioned two traits for Safe and Box actors,
and make sure the Banking example can be implemented using it.

TODO: Only support Ask pattern for Safe ActorRefs, i.e. enforce `sender` to only allow
sending safe messages, and likewise for the ask method. This should be handled on trait level.

TODO: Move Terminated et. al. to LaCasa actor namespace.
*/