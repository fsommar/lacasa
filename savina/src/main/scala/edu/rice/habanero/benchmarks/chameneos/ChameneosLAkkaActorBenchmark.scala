package edu.rice.habanero.benchmarks.chameneos

import akka.lacasa.actor.{ActorRef, Props}
import edu.rice.habanero.actors.{LAkkaActor => Actor, LAkkaActorState => ActorState}
import edu.rice.habanero.benchmarks.{Benchmark, BenchmarkRunner}
import edu.rice.habanero.benchmarks.chameneos.ChameneosConfig._

/**
 *
 * @author <a href="http://shams.web.rice.edu/">Shams Imam</a> (shams@rice.edu)
 */
object ChameneosLAkkaActorBenchmark {

  def main(args: Array[String]) {
    BenchmarkRunner.runBenchmark(args, new ChameneosLAkkaActorBenchmark)
  }

  private final class ChameneosLAkkaActorBenchmark extends Benchmark {
    def initialize(args: Array[String]) {
      ChameneosConfig.parseArgs(args)
    }

    def printArgInfo() {
      ChameneosConfig.printArgs()
    }

    def runIteration() {

      val system = ActorState.newActorSystem("Chameneos")

      val mallActor = system.actorOf(Props(
        new ChameneosMallActor(
          ChameneosConfig.numMeetings, ChameneosConfig.numChameneos)))

      ActorState.startActor(mallActor)

      ActorState.awaitTermination(system)
    }

    def cleanupIteration(lastIteration: Boolean, execTimeMillis: Double) {
    }
  }

  private class ChameneosMallActor(var n: Int, numChameneos: Int) extends Actor[Message] {

    private var waitingChameneo: ActorRef = null
    private var sumMeetings: Int = 0
    private var numFaded: Int = 0

    protected override def onPostStart() {
      startChameneos()
    }

    private def startChameneos() {
      Array.tabulate[ActorRef](numChameneos)(i => {
        val color = Color.values(i % 3)
        val loopChamenos = context.system.actorOf(Props(new ChameneosChameneoActor(self, color, i)))
        ActorState.startActor(loopChamenos)
        loopChamenos
      })
    }

    override def process(msg: Message) {
      msg match {
        case message: MeetingCountMsg =>
          numFaded = numFaded + 1
          sumMeetings = sumMeetings + message.count
          if (numFaded == numChameneos) {
            exit()
          }
        case message: MeetMsg =>
          if (n > 0) {
            if (waitingChameneo == null) {
              val sender = message.sender.asInstanceOf[ActorRef]
              waitingChameneo = sender
            } else {
              n = n - 1
              waitingChameneo ! msg
              waitingChameneo = null
            }
          } else {
            val sender = message.sender.asInstanceOf[ActorRef]
            sender ! ExitMsg(self)
          }
      }
    }
  }

  private class ChameneosChameneoActor(mall: ActorRef, var color: Color, id: Int)
    extends Actor[Message] {

    private var meetings: Int = 0

    protected override def onPostStart() {
      mall ! MeetMsg(color, self)
    }

    override def process(msg: Message) {
      msg match {
        case message: MeetMsg =>
          val otherColor: Color = message.color
          val sender = message.sender.asInstanceOf[ActorRef]
          color = color.complement(otherColor)
          meetings = meetings + 1
          sender ! ChangeMsg(color, self)
          mall ! MeetMsg(color, self)
        case message: ChangeMsg =>
          color = message.color
          meetings = meetings + 1
          mall ! MeetMsg(color, self)
        case message: ExitMsg =>
          val sender = message.sender.asInstanceOf[ActorRef]
          color = Color.FADED
          sender ! MeetingCountMsg(meetings, self)
          exit()
      }
    }
  }

}
