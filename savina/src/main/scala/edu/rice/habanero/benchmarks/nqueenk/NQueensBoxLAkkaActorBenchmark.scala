package edu.rice.habanero.benchmarks.nqueenk

import akka.lacasa.actor.{ActorRef, Props, Safe}
import edu.rice.habanero.actors.{LAkkaActor => Actor, LAkkaActorState => ActorState}
import edu.rice.habanero.benchmarks.{Benchmark, BenchmarkRunner}
import edu.rice.habanero.benchmarks.nqueenk.NQueensConfig._
import lacasa.Box

/**
 * @author <a href="http://shams.web.rice.edu/">Shams Imam</a> (shams@rice.edu)
 */
object NQueensBoxLAkkaActorBenchmark {

  def main(args: Array[String]) {
    BenchmarkRunner.runBenchmark(args, new NQueensBoxLAkkaActorBenchmark)
  }

  private final class NQueensBoxLAkkaActorBenchmark extends Benchmark {
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
                            data: Array[Int],
                            depth: Int)

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
      Box.mkBoxFor(
        WorkMessage(priority(priorities), new Array[Int](0), 0)) { packed =>
        sendWork(packed.box)(packed.access)
      }
    }

    private def sendWork(msg: Box[WorkMessage])(implicit acc: msg.Access): Unit = {
      workers(messageCounter).tellAndThen(msg) { () =>
        messageCounter = (messageCounter + 1) % numWorkers
        numWorkSent += 1
      }
    }

    override def process(msg: Box[Any])(implicit acc: msg.Access): Unit =
      if (msg.isBoxOf[WorkMessage]) {
        msg.asBoxOf[WorkMessage] { packed =>
          sendWork(packed.box)(packed.access)
        }
      }

    override def process(theMsg: Message) {
      theMsg match {
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

  private class Worker(master: ActorRef, id: Int) extends Actor[StopMessage] {

    private final val threshold: Int = THRESHOLD
    private final val size: Int = SIZE

    override def process(msg: Box[Any])(implicit acc: msg.Access): Unit = msg open {
      case msg: WorkMessage if size == msg.depth || msg.depth >= threshold =>
        nqueensKernelSeq(msg.data, msg.depth)
        master ! DoneMessage()
      case msg: WorkMessage =>
        nqueensKernelPar(msg)
    }

    override def process(msg: StopMessage) {
      master ! msg
      exit()
    }

    def nqueensKernelPar(workMessage: WorkMessage) {
      val depth: Int = workMessage.depth
      val newPriority: Int = workMessage.priority - 1
      val newDepth: Int = depth + 1
      val validBoards =
        (0 until size)
        .map { i =>
          val newData: Array[Int] = new Array[Int](newDepth)
          System.arraycopy(workMessage.data, 0, newData, 0, depth)
          newData(depth) = i
          newData
        }
        .filter { boardValid(newDepth, _) }

      lacasa.Utils.loopAndThen(validBoards.toIterator)({ newData =>
        Box.mkBoxFor(WorkMessage(priority(newPriority), newData, newDepth)) { packed =>
          implicit val acc = packed.access
          master !! packed.box
        }
      })({ () =>
        master ! DoneMessage()
      })
    }

    def nqueensKernelSeq(a: Array[Int], depth: Int) {
      if (size == depth) {
        master ! ResultMessage()
      } else {
        val b: Array[Int] = new Array[Int](depth + 1)
        for (i <- 0 until size) {
          System.arraycopy(a, 0, b, 0, depth)
          b(depth) = i
          if (boardValid(depth + 1, b)) {
            nqueensKernelSeq(b, depth + 1)
          }
        }
      }
    }

  }

}
