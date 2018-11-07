package edu.rice.habanero.benchmarks.nqueenk

import akka.actor.{ActorRef, Props}
import edu.rice.habanero.actors.{AkkaActor => Actor, AkkaActorState => ActorState}
import edu.rice.habanero.benchmarks.{Benchmark, BenchmarkRunner}
import edu.rice.habanero.benchmarks.nqueenk.NQueensConfig._

/**
 * @author <a href="http://shams.web.rice.edu/">Shams Imam</a> (shams@rice.edu)
 */
object NQueensAkkaActorBenchmark {

  def main(args: Array[String]) {
    BenchmarkRunner.runBenchmark(args, new NQueensAkkaActorBenchmark)
  }

  private final class NQueensAkkaActorBenchmark extends Benchmark {
    def initialize(args: Array[String]) {
      parseArgs(args)
    }

    def printArgInfo() {
      printArgs()
    }

    def runIteration() {
      val numWorkers: Int = NUM_WORKERS
      val priorities: Int = PRIORITIES

      val system = ActorState.newActorSystem("NQueens")

      val master = system.actorOf(Props(new Master(numWorkers, priorities)))
      ActorState.startActor(master)

      ActorState.awaitTermination(system)
    }

    def cleanupIteration(lastIteration: Boolean, execTimeMillis: Double) {
    }
  }

  
  sealed trait Message

  case class WorkMessage(priority: Int,
                            data: Array[Int],
                            depth: Int) extends Message

  case class DoneMessage() extends Message

  case class ResultMessage() extends Message

  case class StopMessage() extends Message

  private class Master(numWorkers: Int, priorities: Int) extends Actor[Message] {

    private val solutionsLimit = SOLUTIONS_LIMIT
    private final val workers = new Array[ActorRef](numWorkers)
    private var messageCounter: Int = 0
    private var numWorkersTerminated: Int = 0
    private var numWorkSent: Int = 0
    private var numWorkCompleted: Int = 0
    private var resultCounter = 0

    override def onPostStart() {
      var i: Int = 0
      while (i < numWorkers) {
        workers(i) = context.system.actorOf(Props(new Worker(self, i)))
        ActorState.startActor(workers(i))
        i += 1
      }
      val inArray: Array[Int] = new Array[Int](0)
      val workMessage = WorkMessage(priorities, inArray, 0)
      sendWork(workMessage)
    }

    private def sendWork(workMessage: WorkMessage) {
      workers(messageCounter) ! workMessage
      messageCounter = (messageCounter + 1) % numWorkers
      numWorkSent += 1
    }

    override def process(theMsg: Message) {
      theMsg match {
        case workMessage: WorkMessage =>
          sendWork(workMessage)
        case _: ResultMessage =>
          resultCounter += 1
          if (resultCounter == solutionsLimit) {
            requestWorkersToTerminate()
          }
        case _: DoneMessage =>
          numWorkCompleted += 1
          if (numWorkCompleted == numWorkSent) {
            requestWorkersToTerminate()
          }
        case _: StopMessage =>
          numWorkersTerminated += 1
          if (numWorkersTerminated == numWorkers) {
            exit()
          }
        case _ =>
      }
    }

    def requestWorkersToTerminate() {
      var i: Int = 0
      while (i < numWorkers) {
        workers(i) ! StopMessage()
        i += 1
      }
    }
  }

  private class Worker(master: ActorRef, id: Int) extends Actor[Message] {

    private final val threshold: Int = THRESHOLD
    private final val size: Int = SIZE

    override def process(theMsg: Message) {
      theMsg match {
        case workMessage: WorkMessage =>
          nqueensKernelPar(workMessage)
          master ! DoneMessage()
        case _: StopMessage =>
          master ! theMsg
          exit()
        case _ =>
      }
    }

    private[nqueenk] def nqueensKernelPar(workMessage: WorkMessage) {
      val a: Array[Int] = workMessage.data
      val depth: Int = workMessage.depth
      if (size == depth) {
        master ! ResultMessage()
      } else if (depth >= threshold) {
        nqueensKernelSeq(a, depth)
      } else {
        val newPriority: Int = workMessage.priority - 1
        val newDepth: Int = depth + 1
        var i: Int = 0
        while (i < size) {
          val b: Array[Int] = new Array[Int](newDepth)
          System.arraycopy(a, 0, b, 0, depth)
          b(depth) = i
          if (boardValid(newDepth, b)) {
            master ! WorkMessage(priority(newPriority), b, newDepth)
          }
          i += 1
        }
      }
    }

    private[nqueenk] def nqueensKernelSeq(a: Array[Int], depth: Int) {
      if (size == depth) {
        master ! ResultMessage()
      }
      else {
        val b: Array[Int] = new Array[Int](depth + 1)

        var i: Int = 0
        while (i < size) {
          System.arraycopy(a, 0, b, 0, depth)
          b(depth) = i
          if (boardValid(depth + 1, b)) {
            nqueensKernelSeq(b, depth + 1)
          }

          i += 1
        }
      }
    }
  }

}
