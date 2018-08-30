package akka.actor.lacasa

import scala.concurrent.Future

import akka.actor.{ActorPath, RootActorPath, Address, Terminated}
import akka.util.Timeout

import lacasa.{Safe}

abstract class ActorSystem extends ActorRef {
  /**
   * The name of this actor system, used to distinguish multiple ones within
   * the same JVM & class loader.
   */
  def name: String
  def actorOf(props: Props, name: String): ActorRef
  def terminate(): scala.concurrent.Future[Terminated]
}

object ActorSystem {

  def apply(name: String): ActorSystem = wrap(akka.actor.ActorSystem(name))

  def wrap(untyped: akka.actor.ActorSystem): ActorSystem =
    ActorSystemAdapter(untyped.asInstanceOf[akka.actor.ActorSystemImpl])
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

  import akka.dispatch.ExecutionContexts.sameThreadExecutionContext

  override def terminate(): scala.concurrent.Future[Terminated] =
    untyped.terminate()//.map(t ⇒ Terminated(ActorRefAdapter(t.actor))(null))(sameThreadExecutionContext)
  // override lazy val whenTerminated: scala.concurrent.Future[akka.actor.typed.Terminated] =
  //   untyped.whenTerminated.map(t ⇒ Terminated(ActorRefAdapter(t.actor))(null))(sameThreadExecutionContext)
  // override lazy val getWhenTerminated: CompletionStage[akka.actor.typed.Terminated] =
  //   FutureConverters.toJava(whenTerminated)

  def systemActorOf(name: String, props: Props)(implicit timeout: Timeout): Future[ActorRef] = {
    val ref = untyped.systemActorOf(PropsAdapter(props), name)
    Future.successful(ActorRefAdapter(ref))
  }

  override def actorOf(props: Props, name: String): ActorRef =
    ActorRefAdapter(untyped.actorOf(PropsAdapter(props), name))

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
