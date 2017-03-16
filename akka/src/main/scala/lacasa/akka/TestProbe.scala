package lacasa.akka.testkit

import akka.testkit.{TestProbe => AkkaTestProbe}
import akka.actor.{ActorSystem, ActorRef => AkkaActorRef}
import lacasa.{Box, Packed, Safe}
import lacasa.akka.actor.{ActorRef, SafeWrapper}
import scala.concurrent.duration.Duration

class TestProbe(application: ActorSystem, name: String) extends AkkaTestProbe(application, name) {
  def this(application: ActorSystem) = {
    this(application, "safeTestProbe")
  }

  def sendSafe[T: Safe](ref: ActorRef, msg: T) = {
    this.send(ref.ref, new SafeWrapper(msg))
  }

  /**
   * The type of the boxed messages aren't statically checked when testing,
   * and is thereby left to the user's own discretion.
   */
  def sendBox[T](ref: ActorRef, msg: T) = {
    this.send(ref.ref, Box.make(msg).pack())
  }

  override def receiveOne(max: Duration): AnyRef = {
    super.receiveOne(max) match {
      case x: Packed[_] =>
        x.box.instance.asInstanceOf[AnyRef]
      case SafeWrapper(x) =>
        x.asInstanceOf[AnyRef]
      case x =>
        x
    }
  }
}

object TestProbe {
  def apply()(implicit system: ActorSystem) = new TestProbe(system)
  def apply(name: String)(implicit system: ActorSystem) = new TestProbe(system, name)
}
