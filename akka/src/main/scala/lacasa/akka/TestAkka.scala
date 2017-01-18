package lacasa.akka

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}

import lacasa.{Box, CanAccess, NoReturnControl, Packed, Safe}
import Box.mkBox


case class Message(var s: String) {
  def this() = this("")
}

object Message {
  implicit val messageIsSafe = new Safe[Message]{}
}

class MyActor extends SafeActor[Message] {
  val log = Logging(context.system, this)

  override def init() = {
    SafeActorRef[Message](self) >!< new Message("init")
  }

  def receive(msg: Box[Message])(implicit acc: CanAccess { type C = msg.C }): Unit = {
    msg.open({ (m: Message) =>
      m match {
        case Message("test") => log.info("received test")
        case Message("init") => log.info("received msg from init")
        case _ => log.info("received unknown message")
      }
    })
  }
}


object TestAkka {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("test-system")
    val a = SafeActorRef[Message](system.actorOf(Props[MyActor]))

    SafeActorRef.init(a)

    try {

      mkBox[Message] { packed =>
        implicit val access = packed.access
        val box: packed.box.type = packed.box
        box.open({ obj =>
          obj.s = "test"
        })
        a ! box
      }
      // Alternatively `a >!< new Message("test")`

    } catch {
      case _: NoReturnControl =>
        Box.uncheckedCatchControl
        Thread.sleep(2000)
        system.terminate()
    }

  }
}
