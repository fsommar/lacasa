package akka.lacasa.actor

import akka.actor.{Actor => AkkaActor, ActorContext => AkkaActorContext, ActorRef => AkkaActorRef,
                   ActorPath, RootActorPath, Address, Terminated, ActorInitializationException}

import lacasa.{Box, Packed}


object ActorRef {

  implicit def actorRefIsSafe[T <: SafeActorRef]: lacasa.Safe[T] = new lacasa.Safe[T] {}

  implicit final class SafeActorRefOps(val ref: SafeActorRef) extends AnyVal {
    def ![T: lacasa.Safe](msg: T)(implicit sender: ActorRef = ActorRef.noSender): Unit = ref.tell(msg, sender)
  }

  implicit final class ActorRefOps(val ref: ActorRef) extends AnyVal {
    def !!(msg: Box[Any])(implicit access: msg.Access): Nothing =
      ref.tell(msg)(access)

    def tellAndThen[S](msg: Box[Any])(cont: () => S)(implicit access: msg.Access): Nothing = {
      Box.unsafe(ref.tell(msg)(access))
      cont()
      msg.consume
    }
  }
  

  implicit val actorRefLogSource: akka.event.LogSource[ActorRef] = new akka.event.LogSource[ActorRef] {
    def genString(a: ActorRef) = a.path.toString
  }

  val noSender = ActorRefAdapter(akka.actor.ActorRef.noSender)

}

trait SafeActorRef extends java.lang.Comparable[ActorRef] {

  def tell[T: lacasa.Safe](msg: T, sender: ActorRef): Unit

  def path: ActorPath
}

trait ActorRef extends SafeActorRef {
  def tell(msg: Box[Any])(implicit access: msg.Access): Nothing
}

private[akka] trait ActorRefImpl extends ActorRef {
  // def sendSystem(signal: SystemMessage): Unit
  // def isLocal: Boolean

  /**
   * Comparison takes path and the unique id of the actor cell into account.
   */
  final override def compareTo(other: ActorRef) = {
    val x = this.path compareTo other.path
    if (x == 0) this.path.uid compareTo other.path.uid
    else x
  }

  final override def hashCode: Int = path.uid

  /**
   * Equals takes path and the unique id of the actor cell into account.
   */
  final override def equals(that: Any): Boolean = that match {
    case other: ActorRef ⇒ path.uid == other.path.uid && path == other.path
    case _               ⇒ false
  }

  override def toString: String = s"Actor[${path}#${path.uid}]"
}

private[akka] class ActorRefAdapter(val unsafe: AkkaActorRef)
  extends SafeActorRef with ActorRefImpl {

  override def path: ActorPath = unsafe.path

  override def tell[T: lacasa.Safe](msg: T, sender: ActorRef): Unit =
    unsafe.tell(new SafeWrapper(msg), ActorRefAdapter.toUnsafe(sender))

  // TODO: Work around package private methods in lacasa/akka internals,
  // e.g., by creating an unsafe evidence that needs to exist in order to
  // use .pack()
  override def tell(msg: Box[Any])(implicit access: msg.Access): Nothing = {
    unsafe ! lacasa.PackABoxHelper.pack(msg)
    throw new lacasa.PackABoxHelper.NoReturnControl
  }
}

object ActorRefAdapter {
  def apply(unsafe: AkkaActorRef): ActorRef = new ActorRefAdapter(unsafe)

  def toUnsafe(ref: ActorRef): AkkaActorRef =
    ref match {
      case adapter: ActorRefAdapter   ⇒ adapter.unsafe
      case system: ActorSystemAdapter ⇒ system.unsafe.guardian
      case _ ⇒
        throw new UnsupportedOperationException("only adapted unsafe ActorRefs permissible " +
          s"($ref of class ${ref.getClass.getName})")
    }
}
