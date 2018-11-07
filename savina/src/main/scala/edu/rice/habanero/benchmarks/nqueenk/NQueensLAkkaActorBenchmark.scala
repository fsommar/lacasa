package edu.rice.habanero.benchmarks.nqueenk

import akka.lacasa.actor.{ActorRef, Props, Safe}
import edu.rice.habanero.actors.{LAkkaActor => Actor, LAkkaActorState => ActorState}
import edu.rice.habanero.benchmarks.{Benchmark, BenchmarkRunner}
import edu.rice.habanero.benchmarks.nqueenk.NQueensConfig._
import scala.collection.immutable.Vector

/**
 * @author <a href="http://shams.web.rice.edu/">Shams Imam</a> (shams@rice.edu)
 */
object NQueensLAkkaActorBenchmark {

  def main(args: Array[String]) {
    BenchmarkRunner.runBenchmark(args, new NQueensLAkkaActorBenchmark)
  }

  private final class NQueensLAkkaActorBenchmark extends Benchmark {
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

  
  sealed trait Message extends Safe

  case class WorkMessage(priority: Int,
                            data: Vector[Int],
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
      val workMessage = WorkMessage(priorities, Vector(0), 0)
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
        case msg: WorkMessage if size == msg.depth || msg.depth >= threshold =>
          nqueensKernelSeq(msg.data, msg.depth)
          master ! DoneMessage()
        case msg: WorkMessage =>
          nqueensKernelPar(msg)
        case _: StopMessage =>
          master ! theMsg
          exit()
        case _ =>
      }
    }

    def nqueensKernelPar(workMessage: WorkMessage) {
      val depth: Int = workMessage.depth
      val newPriority: Int = workMessage.priority - 1
      val newDepth: Int = depth + 1
      (0 until size)
        .map {
          workMessage.data
            .padTo(newDepth, 0)
            .updated(depth, _)
        }
        .filter { NQueensConfig.boardValid(newDepth, _) }
        .foreach { master ! WorkMessage(priority(newPriority), _, newDepth) }

      master ! DoneMessage()
    }

    def nqueensKernelSeq(a: Vector[Int], depth: Int) {
      if (size == depth) {
        master ! ResultMessage()
      } else {
        val base = a.padTo(depth + 1, 0)
        for (i <- 0 until size) {
          val current = base.updated(depth, i)
          if (boardValid(depth + 1, current)) {
            nqueensKernelSeq(current, depth + 1)
          }
        }
      }
    }

  }

}
