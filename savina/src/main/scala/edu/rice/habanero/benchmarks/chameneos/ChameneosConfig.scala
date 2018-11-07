package edu.rice.habanero.benchmarks.chameneos

import edu.rice.habanero.benchmarks.BenchmarkRunner
import akka.lacasa.actor.Safe

object ChameneosConfig {

  var numChameneos: Int = 100

  var numMeetings: Int = 200000

  var numMailboxes: Int = 1

  var usePriorities: Boolean = true

  var debug: Boolean = false

  def parseArgs(args: Array[String]): Unit = {
    var i: Int = 0
    while (i < args.length) {
      val loopOptionKey: String = args(i)
      loopOptionKey match {
        case "-numChameneos" | "-c" =>
          i += 1
          numChameneos = java.lang.Integer.parseInt(args(i))
        case "-numMeetings" | "-m" =>
          i += 1
          numMeetings = java.lang.Integer.parseInt(args(i))
        case "-numChannels" | "-numMailboxes" | "-nm" =>
          i += 1
          numMailboxes = java.lang.Integer.parseInt(args(i))
        case "-p" =>
          i += 1
          usePriorities = java.lang.Boolean.parseBoolean(args(i))
        case "-debug" | "-verbose" => debug = true

      }
      i += 1
    }
  }

  def printArgs(): Unit = {
    printf(BenchmarkRunner.argOutputFormat,
                      "num chameneos",
                      numChameneos)
    printf(BenchmarkRunner.argOutputFormat,
                      "num meetings",
                      numMeetings)
    printf(BenchmarkRunner.argOutputFormat,
                      "num mailboxes",
                      numMailboxes)
    printf(BenchmarkRunner.argOutputFormat,
                      "use priorities",
                      usePriorities)
    printf(BenchmarkRunner.argOutputFormat, "debug", debug)
  }

   trait Color extends Safe {
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

  sealed trait Message extends Safe

  case class MeetMsg(color: Color, sender: AnyRef) extends Message

  case class ChangeMsg(color: Color, sender: AnyRef) extends Message

  case class MeetingCountMsg(count: Int, sender: AnyRef) extends Message
  
  case class ExitMsg(sender: AnyRef) extends Message

}
