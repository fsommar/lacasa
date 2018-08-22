package akka.actor.lacasa

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import scala.reflect.runtime.universe._

import akka.actor.{Actor => AkkaActor, ActorContext => AkkaActorContext, ActorRef => AkkaActorRef,
                   ActorPath, RootActorPath, Address}
import akka.event.LoggingAdapter
import akka.util.Timeout

import lacasa.{Box, Packed, Safe}

object ActorRef {
  implicit final class ActorRefOps(val ref: ActorRef) extends AnyVal {
    def ![T: Safe](msg: T): Unit = ref.tell(msg)
    // def !(msg: Box[Any])(implicit access: msg.Access): Nothing = ref.tell(msg)(access)
  }
}

trait ActorRef extends java.lang.Comparable[ActorRef] {

  def tell[T: Safe](msg: T): Unit

  // def tell(msg: Box[Any])(implicit access: msg.Access): Nothing

  def path: ActorPath
}

/**
 * Every ActorRef is also an ActorRefImpl, but these two methods shall be
 * completely hidden from client code.
 */
private trait ActorRefImpl extends ActorRef {
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

private class ActorRefAdapter(val unsafe: AkkaActorRef)
  extends ActorRef with ActorRefImpl {

  override def path: ActorPath = unsafe.path

  override def tell[T: Safe](msg: T): Unit = {
    unsafe ! msg
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
        throw new UnsupportedOperationException("only adapted untyped ActorRefs permissible " +
          s"($ref of class ${ref.getClass.getName})")
    }
}


/**
 * An ActorSystem is home to a hierarchy of Actors. It is created using
 * [[ActorSystem#apply]] from a [[Behavior]] object that describes the root
 * Actor of this hierarchy and which will create all other Actors beneath it.
 * A system also implements the [[ActorRef]] type, and sending a message to
 * the system directs that message to the root Actor.
 *
 * Not for user extension.
 */
abstract class ActorSystem extends ActorRef {
  /**
   * The name of this actor system, used to distinguish multiple ones within
   * the same JVM & class loader.
   */
  def name: String
}

object ActorSystem {

  /**
   * Scala API: Create an ActorSystem
   */
  def apply(name: String): ActorSystem = createInternal(name, akka.actor.Props.empty)

  /**
   * Create an ActorSystem based on the untyped [[akka.actor.ActorSystem]]
   * which runs Akka Typed [[Behavior]] on an emulation layer. In this
   * system typed and untyped actors can coexist.
   */
  private def createInternal[T](name: String,
                                props: akka.actor.Props): ActorSystem = {
    val cl = akka.actor.ActorSystem.findClassLoader()
    val appConfig = com.typesafe.config.ConfigFactory.load(cl)

    val untyped = new akka.actor.ActorSystemImpl(name, appConfig, cl, None, Some(props))
    untyped.start()

    ActorSystemAdapter.AdapterExtension(untyped).adapter
  }

  /**
   * Wrap an untyped [[akka.actor.ActorSystem]] such that it can be used from
   * Akka Typed [[Behavior]].
   */
  def wrap(untyped: akka.actor.ActorSystem): ActorSystem =
    ActorSystemAdapter.AdapterExtension(untyped.asInstanceOf[akka.actor.ActorSystemImpl]).adapter
}

private class ActorSystemAdapter(val untyped: akka.actor.ActorSystemImpl)
  extends ActorSystem with ActorRef with ActorRefImpl {
  // untyped.assertInitialized()

  // Members declared in akka.actor.typed.ActorRef
  override def tell[T: Safe](msg: T): Unit = {
    untyped.guardian ! msg
  }

  // override def isLocal: Boolean = true
  // override def sendSystem(signal: internal.SystemMessage): Unit = sendSystemMessage(untyped.guardian, signal)

  final override val path: ActorPath = RootActorPath(Address("akka", untyped.name)) / "user"

  override def toString: String = untyped.toString

  // Members declared in akka.actor.typed.ActorSystem
  // override def deadLetters[U]: ActorRef[U] = ActorRefAdapter(untyped.deadLetters)
  // override def dispatchers: Dispatchers = new Dispatchers {
  //   override def lookup(selector: DispatcherSelector): ExecutionContextExecutor =
  //     selector match {
  //       case DispatcherDefault(_)         ⇒ untyped.dispatcher
  //       case DispatcherFromConfig(str, _) ⇒ untyped.dispatchers.lookup(str)
  //     }
  //   override def shutdown(): Unit = () // there was no shutdown in untyped Akka
  // }
  // override def dynamicAccess: a.DynamicAccess = untyped.dynamicAccess
  // implicit override def executionContext: scala.concurrent.ExecutionContextExecutor = untyped.dispatcher
  // override val log: Logger = new LoggerAdapterImpl(untyped.eventStream, getClass, name, untyped.logFilter)
  // override def logConfiguration(): Unit = untyped.logConfiguration()
  override def name: String = untyped.name
  // override def scheduler: akka.actor.Scheduler = untyped.scheduler
  // override def settings: Settings = new Settings(untyped.settings)
  // override def startTime: Long = untyped.startTime
  // override def threadFactory: java.util.concurrent.ThreadFactory = untyped.threadFactory
  // override def uptime: Long = untyped.uptime
  // override def printTree: String = untyped.printTree

  // import akka.dispatch.ExecutionContexts.sameThreadExecutionContext

  // override def terminate(): scala.concurrent.Future[akka.actor.typed.Terminated] =
  //   untyped.terminate().map(t ⇒ Terminated(ActorRefAdapter(t.actor))(null))(sameThreadExecutionContext)
  // override lazy val whenTerminated: scala.concurrent.Future[akka.actor.typed.Terminated] =
  //   untyped.whenTerminated.map(t ⇒ Terminated(ActorRefAdapter(t.actor))(null))(sameThreadExecutionContext)
  // override lazy val getWhenTerminated: CompletionStage[akka.actor.typed.Terminated] =
  //   FutureConverters.toJava(whenTerminated)

  def systemActorOf(name: String, props: akka.actor.Props)(implicit timeout: Timeout): Future[ActorRef] = {
    val ref = untyped.systemActorOf(props, name)
    Future.successful(ActorRefAdapter(ref))
  }

}

private object ActorSystemAdapter {
  def apply(untyped: akka.actor.ActorSystem): ActorSystem = AdapterExtension(untyped).adapter

  // to make sure we do never create more than one adapter for the same actor system
  class AdapterExtension(system: akka.actor.ExtendedActorSystem) extends akka.actor.Extension {
    val adapter = new ActorSystemAdapter(system.asInstanceOf[akka.actor.ActorSystemImpl])
  }

  object AdapterExtension extends akka.actor.ExtensionId[AdapterExtension] with akka.actor.ExtensionIdProvider {
    override def get(system: akka.actor.ActorSystem): AdapterExtension = super.get(system)
    override def lookup() = AdapterExtension
    override def createExtension(system: akka.actor.ExtendedActorSystem): AdapterExtension =
      new AdapterExtension(system)
  }
}

trait Actor {
  def receive(msg: Box[Any])(implicit access: msg.Access): Nothing
}

private case class SafeWrapper[T](value: T) {
  implicit val safeEv: Safe[T] = implicitly
}

private class ActorAdapter(_actor: Actor) extends AkkaActor {

  private var _ctx: ActorContextAdapter = _
  def ctx: ActorContextAdapter =
    if (_ctx ne null) _ctx
    else throw new IllegalStateException("Context was accessed before safe actor was started.")

  def receive = running

  def running: Receive = {
    case packed: Packed[_] =>
      _actor.receive(packed.box)(packed.access)

    case x: SafeWrapper[_] =>
      implicit val ev = x.safeEv
      ???

    case x =>
      ???
  }

  protected def start(): Unit = {
    context.become(running)
    initializeContext()
  }

  override def postRestart(reason: Throwable): Unit = {
    initializeContext()
  }

  protected def initializeContext(): Unit = {
    _ctx = new ActorContextAdapter(context)
  }
}

private final class ActorContextAdapter(val untyped: akka.actor.ActorContext) extends ActorContextImpl {
  import akka.actor.Props
  import ActorRefAdapter.toUntyped

  override def self = ActorRefAdapter(untyped.self)
  override val system = ActorSystemAdapter(untyped.system)
  override def children = untyped.children.map(ActorRefAdapter(_))
  override def child(name: String) = untyped.child(name).map(ActorRefAdapter(_))
  override def spawn(name: String, props: Props = Props.empty) =
    ActorContextAdapter.spawn(untyped, name, props)
  override def stop(child: ActorRef): Unit =
    if (child.path.parent == self.path) { // only if a direct child
      toUntyped(child) match {
        case f: akka.actor.FunctionRef ⇒
          val cell = untyped.asInstanceOf[akka.actor.ActorCell]
          cell.removeFunctionRef(f)
        case c ⇒
          untyped.child(child.path.name) match {
            case Some(`c`) ⇒
              untyped.stop(c)
            case _ ⇒
            // child that was already stopped
          }
      }
    } else {
      throw new IllegalArgumentException(
        "Only direct children of an actor can be stopped through the actor context, " +
          s"but [$child] is not a child of [$self]. Stopping other actors has to be expressed as " +
          "an explicit stop message that the actor accepts.")
    }
}

private object ActorContextAdapter {

  def spawn(ctx: akka.actor.ActorContext, name: String, props: akka.actor.Props): ActorRef = {
    try {
      ActorRefAdapter(ctx.actorOf(props, name))
    } catch {
      case _: Throwable ⇒
        throw new java.lang.IllegalArgumentException("Remote deployment not allowed for typed actors")
    }
  }

}

private trait ActorContextImpl extends ActorContext with scaladsl.ActorContext {

  private var messageAdapterRef: OptionVal[ActorRef[Any]] = OptionVal.None
  private var _messageAdapters: List[(Class[_], Any ⇒ T)] = Nil
  private var _timer: OptionVal[TimerSchedulerImpl[T]] = OptionVal.None

  // context-shared timer needed to allow for nested timer usage
  def timer: TimerSchedulerImpl[T] = _timer match {
    case OptionVal.Some(timer) ⇒ timer
    case OptionVal.None ⇒
      val timer = new TimerSchedulerImpl[T](this)
      _timer = OptionVal.Some(timer)
      timer
  }

  override def asJava: javadsl.ActorContext[T] = this

  override def asScala: scaladsl.ActorContext[T] = this

  override def getChild(name: String): Optional[ActorRef[Void]] =
    child(name) match {
      case Some(c) ⇒ Optional.of(c.upcast[Void])
      case None    ⇒ Optional.empty()
    }

  override def getChildren: java.util.List[ActorRef[Void]] = {
    val c = children
    val a = new ArrayList[ActorRef[Void]](c.size)
    val i = c.iterator
    while (i.hasNext) a.add(i.next().upcast[Void])
    a
  }

  override def getExecutionContext: ExecutionContextExecutor =
    executionContext

  override def getSelf: akka.actor.typed.ActorRef[T] =
    self

  override def getSystem: akka.actor.typed.ActorSystem[Void] =
    system.asInstanceOf[ActorSystem[Void]]

  override def getLog: Logger = log

  override def setReceiveTimeout(d: java.time.Duration, msg: T): Unit =
    setReceiveTimeout(d.asScala, msg)

  override def schedule[U](delay: java.time.Duration, target: ActorRef[U], msg: U): akka.actor.Cancellable =
    schedule(delay.asScala, target, msg)

  override def spawn[U](behavior: akka.actor.typed.Behavior[U], name: String): akka.actor.typed.ActorRef[U] =
    spawn(behavior, name, Props.empty)

  override def spawnAnonymous[U](behavior: akka.actor.typed.Behavior[U]): akka.actor.typed.ActorRef[U] =
    spawnAnonymous(behavior, Props.empty)

  // Scala API impl
  override def ask[Req, Res](otherActor: ActorRef[Req])(createRequest: ActorRef[Res] ⇒ Req)(mapResponse: Try[Res] ⇒ T)(implicit responseTimeout: Timeout, classTag: ClassTag[Res]): Unit = {
    import akka.actor.typed.scaladsl.AskPattern._
    (otherActor ? createRequest)(responseTimeout, system.scheduler).onComplete(res ⇒
      self.asInstanceOf[ActorRef[AnyRef]] ! new AskResponse(res, mapResponse)
    )
  }

  // Java API impl
  def ask[Req, Res](resClass: Class[Res], otherActor: ActorRef[Req], responseTimeout: Timeout, createRequest: function.Function[ActorRef[Res], Req], applyToResponse: BiFunction[Res, Throwable, T]): Unit = {
    this.ask(otherActor)(createRequest.apply) {
      case Success(message) ⇒ applyToResponse.apply(message, null)
      case Failure(ex)      ⇒ applyToResponse.apply(null.asInstanceOf[Res], ex)
    }(responseTimeout, ClassTag[Res](resClass))
  }

  private[akka] override def spawnMessageAdapter[U](f: U ⇒ T, name: String): ActorRef[U] =
    internalSpawnMessageAdapter(f, name)

  private[akka] override def spawnMessageAdapter[U](f: U ⇒ T): ActorRef[U] =
    internalSpawnMessageAdapter(f, name = "")

  /**
   * INTERNAL API: Needed to make Scala 2.12 compiler happy if spawnMessageAdapter is overloaded for scaladsl/javadsl.
   * Otherwise "ambiguous reference to overloaded definition" because Function is lambda.
   */
  @InternalApi private[akka] def internalSpawnMessageAdapter[U](f: U ⇒ T, name: String): ActorRef[U]

  override def messageAdapter[U: ClassTag](f: U ⇒ T): ActorRef[U] = {
    val messageClass = implicitly[ClassTag[U]].runtimeClass.asInstanceOf[Class[U]]
    internalMessageAdapter(messageClass, f)
  }

  override def messageAdapter[U](messageClass: Class[U], f: JFunction[U, T]): ActorRef[U] =
    internalMessageAdapter(messageClass, f.apply)

  private def internalMessageAdapter[U](messageClass: Class[U], f: U ⇒ T): ActorRef[U] = {
    // replace existing adapter for same class, only one per class is supported to avoid unbounded growth
    // in case "same" adapter is added repeatedly
    _messageAdapters = (messageClass, f.asInstanceOf[Any ⇒ T]) ::
      _messageAdapters.filterNot { case (cls, _) ⇒ cls == messageClass }
    val ref = messageAdapterRef match {
      case OptionVal.Some(ref) ⇒ ref.asInstanceOf[ActorRef[U]]
      case OptionVal.None ⇒
        // AdaptMessage is not really a T, but that is erased
        val ref = internalSpawnMessageAdapter[Any](msg ⇒ AdaptWithRegisteredMessageAdapter(msg).asInstanceOf[T], "adapter")
        messageAdapterRef = OptionVal.Some(ref)
        ref
    }
    ref.asInstanceOf[ActorRef[U]]
  }

  /**
   * INTERNAL API
   */
  @InternalApi private[akka] def messageAdapters: List[(Class[_], Any ⇒ T)] = _messageAdapters
}
