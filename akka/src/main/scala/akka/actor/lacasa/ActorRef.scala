package akka.actor.lacasa

import akka.actor.{Actor => AkkaActor, ActorContext => AkkaActorContext, ActorRef => AkkaActorRef,
                   ActorPath, RootActorPath, Address, Terminated, ActorInitializationException}

import lacasa.{Box, Packed, Safe}


object ActorRef {

  implicit final class ActorRefOps(val ref: ActorRef) extends AnyVal {
    def ![T: Safe](msg: T): Unit = ref.tell(msg)
    // def !(msg: Box[Any])(implicit access: msg.Access): Nothing = ref.tell(msg)(access)
  }

  implicit val actorRefLogSource: akka.event.LogSource[ActorRef] = new akka.event.LogSource[ActorRef] {
    def genString(a: ActorRef) = a.path.toString
  }

  val noSender = ActorRefAdapter(akka.actor.ActorRef.noSender)

}

trait ActorRef extends java.lang.Comparable[ActorRef] {

  def tell[T: Safe](msg: T): Unit

  // def tell(msg: Box[Any])(implicit access: msg.Access): Nothing

  def path: ActorPath
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
  extends ActorRef with ActorRefImpl {

  override def path: ActorPath = unsafe.path

  override def tell[T: Safe](msg: T): Unit = {
    unsafe ! new SafeWrapper(msg)
  }

  // TODO: Work around package private methods in lacasa/akka internals,
  // e.g., by creating an unsafe evidence that needs to exist in order to
  // use .pack()
  // override def tell(msg: Box[Any])(implicit access: msg.Access): Nothing = {
  //   unsafe ! msg.pack()
  // }
}

object ActorRefAdapter {
  def apply(unsafe: AkkaActorRef): ActorRef = new ActorRefAdapter(unsafe)

  def toUntyped(ref: ActorRef): AkkaActorRef =
    ref match {
      case adapter: ActorRefAdapter   ⇒ adapter.unsafe
      case system: ActorSystemAdapter ⇒ system.untyped.guardian
      case _ ⇒
        throw new UnsupportedOperationException("only adapted unsafe ActorRefs permissible " +
          s"($ref of class ${ref.getClass.getName})")
    }
}
