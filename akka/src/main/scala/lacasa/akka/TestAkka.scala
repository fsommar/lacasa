package lacasa.akka

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}
import akka.util.Timeout

import lacasa.{Box, CanAccess, NoReturnControl, Packed, Safe}
import Box._


case class Message(s: String) {
  def this() = this("")
}

object Message {
  implicit val messageIsSafe = new Safe[Message]{}
}

class MyActor extends SafeActor[Message] {
  implicit val ec: ExecutionContext = context.dispatcher

  val log = Logging(context.system, this)
  val askActor = SafeActorRef[Message](context.system.actorOf(Props[MyAskActor]))

  override def init() = {
    self ! new Message("init")
  }

  def receive(box: Box[Message])(implicit acc: CanAccess { type C = box.C }): Unit = {
    val msg = box.extract(identity)
    msg match {
      case Message("test") => log.info("received test")
      case Message("init") =>
        log.info("received msg from init")
        implicit val timeout = Timeout(1 seconds)
        mkBoxOf(new Message("ask")) { packed =>
          implicit val acc = packed.access
          askActor.ask(packed.box) { future =>
            val res = Await.result(future, 1 seconds)
            log.info(s"Got $res")
          }
        }
      case _ => log.info("received unknown message")
    }
  }
}

class MyAskActor extends SafeActor[Message] {
  val log = Logging(context.system, this)

  def receive(box: Box[Message])(implicit acc: CanAccess { type C = box.C }): Unit = {
    val msg = box.extract(identity)
    msg match {
      case Message("ask") =>
        log.info("received ask msg")
        sender() ! new Message("response")
      case _ => log.info("received unknown message")
    }
  }
}


object TestAkka {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("test-system")
    val a = SafeActorRef[Message](system.actorOf(Props[MyActor]))

    SafeActorRef.init(a)
    Thread.sleep(2000)
    system.terminate()
  }
}
