package examples.akka

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChameneosConfig {
    val numChameneos = 100
    val numMeetings = 200000
    val numMailboxes = 1
}

object Chameneos {

  def main(args: Array[String]) {
    val system = ActorSystem("Chameneos")

    val mallActor = system.actorOf(Props(
      new ChameneosMallActor(
        ChameneosConfig.numMeetings, ChameneosConfig.numChameneos)))

    Thread.sleep(6000)
    system.terminate()
  }

  trait Color {
    import Color._
    def complement(otherColor: Color): Color = this match {
      case RED => otherColor match {
        case RED => RED
        case YELLOW => BLUE
        case BLUE => YELLOW
        case FADED => FADED
      }
      case YELLOW => otherColor match {
        case RED => BLUE
        case YELLOW => YELLOW
        case BLUE => RED
        case FADED => FADED
      }
      case BLUE => otherColor match {
        case RED => YELLOW
        case YELLOW => RED
        case BLUE => BLUE
        case FADED => FADED
      }
      case FADED => FADED
    }
  }

  object Color {
    case object RED extends Color
    case object YELLOW extends Color
    case object BLUE extends Color
    case object FADED extends Color

    def values: List[Color] = List(RED, YELLOW, BLUE, FADED)
  }

  private case class MeetMsg(color: Color, sender: ActorRef)

  private case class ChangeMsg(color: Color, sender: ActorRef)

  private case class MeetingCountMsg(count: Int, sender: ActorRef)
  
  private case class ExitMsg(sender: ActorRef)

  private class ChameneosMallActor(var meetingsLeft: Int, numChameneos: Int) extends Actor {

    private var waitingChameneo: ActorRef = null
    private var sumMeetings: Int = 0
    private var numFaded: Int = 0

    Array.tabulate[ActorRef](numChameneos)(i => {
      val color = Color.values(i % 3)
      context.system.actorOf(Props(new ChameneosChameneoActor(self, color, i)))
    })

    override def receive: Receive = {
      case MeetingCountMsg(count, _) =>
        numFaded += 1
        sumMeetings += count
        if (numFaded == numChameneos) {
          context.stop(self)
        }

      case msg @ MeetMsg(_, sender) =>
        if (meetingsLeft > 0) {
          if (waitingChameneo == null) {
            waitingChameneo = msg.sender
          } else {
            meetingsLeft -= 1
            waitingChameneo ! msg
            waitingChameneo = null
          }
        } else {
          sender ! new ExitMsg(self)
        }
    }
  }

  private class ChameneosChameneoActor(mall: ActorRef, var color: Color, id: Int)
    extends Actor {

    private var meetings: Int = 0

    mall ! new MeetMsg(color, self)

    override def receive: Receive = {
      case MeetMsg(otherColor, sender) =>
        color = color.complement(otherColor)
        meetings += 1
        sender ! ChangeMsg(color, self)
        mall ! MeetMsg(color, self)

      case msg: ChangeMsg =>
        color = msg.color
        meetings += 1
        mall ! MeetMsg(color, self)

      case ExitMsg(sender) =>
        color = Color.FADED
        sender ! MeetingCountMsg(meetings, self)
        context.stop(self)
    }
  }

}