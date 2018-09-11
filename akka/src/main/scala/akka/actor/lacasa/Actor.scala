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

import lacasa.{Box, Packed}

trait Safe

object Safe {
  implicit def safeIsLaCasaSafe[T <: Safe]: lacasa.Safe[T] = new lacasa.Safe[T] {}
}

object BaseActor {
  implicit val actorLogSource: akka.event.LogSource[BaseActor] = new akka.event.LogSource[BaseActor] {
    def genString(a: BaseActor) = a.self.path.toString
  }
}

trait BaseActor {

  // A drawback with this specific version is that it's possible to accidentally match on
  // non-Safe types and assume that they will match since the compiler won't complain.
  // Another solution would be to parameterize the trait itself to T and thus make it harder
  // to accidentally match on a non-Safe value. In that case, it would be because a subtype of
  // a Safe type is non-Safe, which isn't allowed anyway (but not enforced).
  def receive[T: lacasa.Safe](msg: T): Unit

  def receive(msg: Box[Any])(implicit acc: msg.Access): Nothing

  def receiveSystem: PartialFunction[SystemMessage, Unit] = AkkaActor.ignoringBehavior

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

  implicit val executionContext: ExecutionContext = context.executionContext

  final def sender(): SafeActorRef = context.sender()
}

trait OnlySafe { self: BaseActor =>
  def receive(msg: Box[Any])(implicit acc: msg.Access): Nothing =
    AkkaActor.emptyBehavior.apply(msg)
}

trait NoSafe { self: BaseActor =>
  def receive[T: lacasa.Safe](msg: T): Unit =
    AkkaActor.emptyBehavior.apply(msg) 
}

trait TypedSafe[U] { self: BaseActor =>
  implicit val tag: scala.reflect.ClassTag[U]
  implicit val safe: lacasa.Safe[U]

  type Receive = PartialFunction[U, Unit]

  override def receive(msg: Box[Any])(implicit acc: msg.Access): Nothing =
    AkkaActor.emptyBehavior.apply(msg)
  
  final override def receive[T: lacasa.Safe](msg: T): Unit =  msg match {
    case x if tag.runtimeClass.isInstance(x) =>
      receive(x.asInstanceOf[U])
    case _ => AkkaActor.emptyBehavior.apply(msg)
  }

  def receive: Receive 
}

abstract class NoSafeActor extends BaseActor with NoSafe

abstract class OnlySafeActor extends BaseActor with OnlySafe

abstract class Actor[T]
  (implicit val tag: scala.reflect.ClassTag[T], implicit val safe: lacasa.Safe[T])
  extends BaseActor with TypedSafe[T]

trait SystemMessage

private[akka] case class SafeWrapper[T](val value: T)(implicit val safe: lacasa.Safe[T])

private class ActorAdapter(_actor: => BaseActor) extends AkkaActor {
  val ref = _actor

  def receive = running

  def running: Receive = {
    case packed: Packed[_] =>
      ref.receive(packed.box)(packed.access)

    case x: SafeWrapper[_] =>
      ref.receive(x.value)(x.safe)

    case x =>
      // TODO: Throw/handle this exceptional case in a better way.
      // E.g., provide an escape hatch for actors to still handle
      // incoming messages from non-safe ("normal") actors.
      AkkaActor.emptyBehavior.apply(x)
  }

  protected def start(): Unit = {
    context.become(running)
  }
}

trait ActorLogging { this: BaseActor ⇒
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