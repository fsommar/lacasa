package lacasa.akka

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}
import akka.util.Timeout

import lacasa.{Box, CanAccess, NoReturnControl, Packed, Safe}
import Box._

import lacasa.akka.actor.{Actor, ActorRef, OnlyBoxReceive}


case class Message(s: String) {
  def this() = this("")
}

object Message {
  implicit val messageIsSafe = new Safe[Message]{}
}

class MyActor extends Actor with OnlyBoxReceive {
  val log = Logging(context.system, this)
  val askActor: ActorRef = context.system.actorOf(Props[MyAskActor])

  override def receive(box: Box[Any])(implicit acc: CanAccess { type C = box.C }): Unit = {
    val msg = box.extract { case msg: Message => msg }
    msg match {
      case Message("buffered") => log.info("got buffered message")
      case Message("test") => log.info("received test")
      case Message("init") =>
        log.info("received msg from init")
        implicit val timeout = Timeout(1 seconds)
        ctx.self ! new Message("buffered")
        val future = askActor.ask(new Message("ask"))
        val res = Await.result(future, 1 seconds)
        log.info(s"Got $res")
      case _ => log.info("received unknown message")
    }
  }
}

class MyAskActor extends Actor {
  val log = Logging(context.system, this)

  override def receive: Receive = {
    case Message("ask") =>
      log.info("received ask msg")
      ctx.sender ! new Message("response")
      log.info("sent response msg")
    case _ => log.info("received unknown message")
  }
}


object TestAkka {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("test-system")
    val a = ActorRef(system.actorOf(Props[MyActor]))

    a ! new Message("init")
    Thread.sleep(2000)
    system.terminate()
  }
}
