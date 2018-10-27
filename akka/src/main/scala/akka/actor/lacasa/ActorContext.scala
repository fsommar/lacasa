package akka.lacasa.actor

import scala.concurrent.{ExecutionContextExecutor}

trait ActorContext {

  /**
   * The identity of this Actor, bound to the lifecycle of this Actor instance.
   * An Actor with the same name that lives before or after this instance will
   * have a different [[ActorRef]].
   *
   * This field is thread-safe and can be called from other threads than the ordinary
   * actor message processing thread, such as [[scala.concurrent.Future]] callbacks.
   */
  def self: ActorRef

  /**
   * The [[ActorSystem]] to which this Actor belongs.
   *
   * This field is thread-safe and can be called from other threads than the ordinary
   * actor message processing thread, such as [[scala.concurrent.Future]] callbacks.
   */
  def system: ActorSystem

  def sender(): SafeActorRef

  /**
   * The list of child Actors created by this Actor during its lifetime that
   * are still alive, in no particular order.
   *
   * *Warning*: This method is not thread-safe and must not be accessed from threads other
   * than the ordinary actor message processing thread, such as [[scala.concurrent.Future]] callbacks.
   */
  def children: Iterable[ActorRef]

  /**
   * The named child Actor if it is alive.
   *
   * *Warning*: This method is not thread-safe and must not be accessed from threads other
   * than the ordinary actor message processing thread, such as [[scala.concurrent.Future]] callbacks.
   */
  def child(name: String): Option[ActorRef]

  /**
   * Create a child Actor from the given [[akka.actor.typed.Behavior]] and with the given name.
   *
   * *Warning*: This method is not thread-safe and must not be accessed from threads other
   * than the ordinary actor message processing thread, such as [[scala.concurrent.Future]] callbacks.
   */
  def spawn(name: String, props: Props = Props.empty): ActorRef

  /**
   * Force the child Actor under the given name to terminate after it finishes
   * processing its current message. Nothing happens if the ActorRef is a child that is already stopped.
   *
   * *Warning*: This method is not thread-safe and must not be accessed from threads other
   * than the ordinary actor message processing thread, such as [[scala.concurrent.Future]] callbacks.
   *
   *  @throws IllegalArgumentException if the given actor ref is not a direct child of this actor
   */
  def stop(child: ActorRef): Unit

  /**
   * This Actor’s execution context. It can be used to run asynchronous tasks
   * like [[scala.concurrent.Future]] operators.
   *
   * This field is thread-safe and can be called from other threads than the ordinary
   * actor message processing thread, such as [[scala.concurrent.Future]] callbacks.
   */
  implicit def executionContext: ExecutionContextExecutor

}

private[akka] object ActorContextAdapter {

  def spawn(ctx: akka.actor.ActorContext, name: String, props: Props): ActorRef = {
    try {
      ActorRefAdapter(ctx.actorOf(props.unsafe, name))
    } catch {
      case _: Throwable ⇒
        throw new java.lang.IllegalArgumentException("Remote deployment not allowed for typed actors")
    }
  }

}

private[akka] final class ActorContextAdapter(val unsafe: akka.actor.ActorContext) extends ActorContext {
  import ActorRefAdapter.toUnsafe

  override def self = ActorRefAdapter(unsafe.self)
  override val system = ActorSystemAdapter(unsafe.system)
  override def sender() = ActorRefAdapter(unsafe.sender())
  override def children = unsafe.children.map(ActorRefAdapter(_))
  override def child(name: String) = unsafe.child(name).map(ActorRefAdapter(_))
  override def spawn(name: String, props: Props = Props.empty) =
    ActorContextAdapter.spawn(unsafe, name, props)
  override def stop(child: ActorRef): Unit = unsafe.stop(toUnsafe(child))
  override def executionContext: ExecutionContextExecutor = unsafe.dispatcher
}