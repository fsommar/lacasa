package akka.lacasa.actor

import scala.concurrent.Future

import akka.actor.{ActorPath, RootActorPath, Address, Terminated}
import akka.util.Timeout

abstract class ActorSystem extends ActorRef {
  def name: String
  def actorOf(props: Props): ActorRef
  def actorOf(props: Props, name: String): ActorRef
  def terminate(): scala.concurrent.Future[Terminated]
}

object ActorSystem {

  def apply(name: String): ActorSystem = wrap(akka.actor.ActorSystem(name))

  def apply(name: String, config: com.typesafe.config.Config): ActorSystem =
    wrap(akka.actor.ActorSystem(name, config))

  def wrap(unsafe: akka.actor.ActorSystem): ActorSystem =
    ActorSystemAdapter(unsafe.asInstanceOf[akka.actor.ActorSystemImpl])
}

private class ActorSystemAdapter(val unsafe: akka.actor.ActorSystemImpl)
  extends ActorSystem with ActorRef with ActorRefImpl {
  // unsafe.assertInitialized()

  // Members declared in akka.lacasa.actor.ActorRef
  override def tell[T: lacasa.Safe](msg: T, sender: ActorRef): Unit =
    unsafe.guardian.tell(msg, ActorRefAdapter.toUnsafe(sender))

  override def tell(msg: lacasa.Box[Any])(implicit acc: msg.Access): Nothing = {
    unsafe.guardian ! lacasa.PackABoxHelper.pack(msg)
    throw new lacasa.PackABoxHelper.NoReturnControl
  }

  // override def isLocal: Boolean = true
  // override def sendSystem(signal: internal.SystemMessage): Unit = sendSystemMessage(unsafe.guardian, signal)

  final override val path: ActorPath = RootActorPath(Address("akka", unsafe.name)) / "user"

  override def toString: String = unsafe.toString

  // Members declared in akka.actor.typed.ActorSystem
  // override def deadLetters[U]: ActorRef[U] = ActorRefAdapter(unsafe.deadLetters)
  // override def dispatchers: Dispatchers = new Dispatchers {
  //   override def lookup(selector: DispatcherSelector): ExecutionContextExecutor =
  //     selector match {
  //       case DispatcherDefault(_)         ⇒ unsafe.dispatcher
  //       case DispatcherFromConfig(str, _) ⇒ unsafe.dispatchers.lookup(str)
  //     }
  //   override def shutdown(): Unit = () // there was no shutdown in unsafe Akka
  // }
  // override def dynamicAccess: a.DynamicAccess = unsafe.dynamicAccess
  // implicit override def executionContext: scala.concurrent.ExecutionContextExecutor = unsafe.dispatcher
  // override val log: Logger = new LoggerAdapterImpl(unsafe.eventStream, getClass, name, unsafe.logFilter)
  // override def logConfiguration(): Unit = unsafe.logConfiguration()
  override def name: String = unsafe.name
  // override def scheduler: akka.actor.Scheduler = unsafe.scheduler
  // override def settings: Settings = new Settings(unsafe.settings)
  // override def startTime: Long = unsafe.startTime
  // override def threadFactory: java.util.concurrent.ThreadFactory = unsafe.threadFactory
  // override def uptime: Long = unsafe.uptime
  // override def printTree: String = unsafe.printTree

  import akka.dispatch.ExecutionContexts.sameThreadExecutionContext

  override def terminate(): scala.concurrent.Future[Terminated] =
    unsafe.terminate()//.map(t ⇒ Terminated(ActorRefAdapter(t.actor))(null))(sameThreadExecutionContext)
  // override lazy val whenTerminated: scala.concurrent.Future[akka.actor.typed.Terminated] =
  //   unsafe.whenTerminated.map(t ⇒ Terminated(ActorRefAdapter(t.actor))(null))(sameThreadExecutionContext)
  // override lazy val getWhenTerminated: CompletionStage[akka.actor.typed.Terminated] =
  //   FutureConverters.toJava(whenTerminated)

  def systemActorOf(name: String, props: Props)(implicit timeout: Timeout): Future[ActorRef] = {
    val ref = unsafe.systemActorOf(PropsAdapter(props), name)
    Future.successful(ActorRefAdapter(ref))
  }

  override def actorOf(props: Props): ActorRef =
    ActorRefAdapter(unsafe.actorOf(PropsAdapter(props)))

  override def actorOf(props: Props, name: String): ActorRef =
    ActorRefAdapter(unsafe.actorOf(PropsAdapter(props), name))

}

private object ActorSystemAdapter {
  def apply(unsafe: akka.actor.ActorSystem): ActorSystem = AdapterExtension(unsafe).adapter

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
