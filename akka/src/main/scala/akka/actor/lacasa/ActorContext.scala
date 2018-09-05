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

private[akka] final class ActorContextAdapter(val untyped: akka.actor.ActorContext) extends ActorContext {
  import ActorRefAdapter.toUntyped

  override def self = ActorRefAdapter(untyped.self)
  override val system = ActorSystemAdapter(untyped.system)
  override def sender() = ActorRefAdapter(untyped.sender())
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
  override def executionContext: ExecutionContextExecutor = untyped.dispatcher
}