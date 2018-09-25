package examples.lacasa

import akka.lacasa.actor.{Actor, ActorRef, ActorSystem, Props, Safe}
import lacasa.Box

object NQueensBoxConfig {

    val SOLUTIONS: Array[Long] = Array(
            1,
            0,
            0,
            2,
            10,     /* 5 */
            4,
            40,
            92,
            352,
            724,    /* 10 */
            2680,
            14200,
            73712,
            365596,
            2279184, /* 15 */
            14772512,
            95815104,
            666090624,
            4968057848L,
            39029188884L /* 20 */
    )
    val MAX_SOLUTIONS = SOLUTIONS.length

    val NUM_WORKERS = 20
    val SIZE = 12
    val THRESHOLD = 4
    val PRIORITIES = 10
    val SOLUTIONS_LIMIT = 1500000

    def boardValid(n: Int, a: Array[Int]): Boolean = {
        for (i <- 0 until n) {
            val p = a(i)
            for (j <- (i + 1) until n) {
                val q = a(j)
                if (q == p || q == p - (j - i) || q == p + (j - i)) {
                    return false
                }
            }
        }
        true
    }

    def priority(x: Int): Int =
      Math.min(PRIORITIES - 1, Math.max(0, x))

}

object NQueensBox {

  def main(args: Array[String]) {
    val numWorkers: Int = NQueensBoxConfig.NUM_WORKERS
    val priorities: Int = NQueensBoxConfig.PRIORITIES

    val system = ActorSystem("NQueensBox")

    system.actorOf(Props(new Master(numWorkers, priorities)))

    Thread.sleep(6000)
    system.terminate()

    val expSolution = NQueensBoxConfig.SOLUTIONS(NQueensBoxConfig.SIZE - 1)
    val actSolution = Master.resultCounter
    val solutionsLimit = NQueensBoxConfig.SOLUTIONS_LIMIT
    val valid = actSolution >= solutionsLimit && actSolution <= expSolution
  }

  sealed trait Message extends Safe
  
  case class WorkMessage(priority: Int, data: Array[Int], depth: Int)

  case class DoneMessage() extends Message

  case class ResultMessage() extends Message

  case class StopMessage() extends Message

  object Master {
    var resultCounter: Long = 0
  }

  private class Master(numWorkers: Int, priorities: Int) extends Actor[Message] {

    private val solutionsLimit = NQueensBoxConfig.SOLUTIONS_LIMIT
    private final val workers = Array.tabulate[ActorRef](numWorkers)(i => {
      context.system.actorOf(Props(new Worker(self, i)))
    })

    private var messageCounter: Int = 0
    private var numWorkersTerminated: Int = 0
    private var numWorkSent: Int = 0
    private var numWorkCompleted: Int = 0

    override def preStart(): Unit = {
      Box.mkBoxFor(
        WorkMessage(
          NQueensBoxConfig.priority(priorities),
          new Array[Int](0),
          0)) { packed =>
        sendWork(packed.box)(packed.access)
      }
    }

    private def sendWork(msg: Box[WorkMessage])(implicit acc: msg.Access): Unit = {
      workers(messageCounter).tellAndThen(msg) { () =>
        messageCounter = (messageCounter + 1) % numWorkers
        numWorkSent += 1
      }
    }

    override def receive(msg: Box[Any])(implicit acc: msg.Access): Unit =
      if (msg.isBoxOf[WorkMessage]) {
        msg.asBoxOf[WorkMessage] { packed =>
          sendWork(packed.box)(packed.access)
        }
      }

    override def receive: Receive = {
      case _: ResultMessage =>
        Master.resultCounter += 1
        if (Master.resultCounter == solutionsLimit) {
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
          println("Done!!")
          context.stop(self)
        }
      case _ =>
    }

    def requestWorkersToTerminate() {
      workers foreach { _ ! StopMessage() }
    }
  }

  private class Worker(master: ActorRef, id: Int) extends Actor[Message] {

    private final val threshold: Int = NQueensBoxConfig.THRESHOLD
    private final val size: Int = NQueensBoxConfig.SIZE

    override def receive(msg: Box[Any])(implicit acc: msg.Access): Unit = msg open {
      case msg: WorkMessage if size == msg.depth || msg.depth >= threshold =>
        nQueensBoxKernelSeq(msg.data, msg.depth)
        master ! DoneMessage()
      case msg: WorkMessage =>
        nQueensBoxKernelPar(msg)
    }

    override def receive: Receive = {
      case msg: StopMessage =>
        master ! msg
        context.stop(self)
    }

    def nQueensBoxKernelPar(workMessage: WorkMessage) {
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
        .filter { NQueensBoxConfig.boardValid(newDepth, _) }

      lacasa.Utils.loopAndThen(validBoards.toIterator)({ newData =>
        Box.mkBoxFor(WorkMessage(NQueensBoxConfig.priority(newPriority), newData, newDepth)) { packed =>
          implicit val acc = packed.access
          master !! packed.box
        }
      })({ () =>
        master ! DoneMessage()
      })
    }

    def nQueensBoxKernelSeq(a: Array[Int], depth: Int) {
      if (size == depth) {
        master ! ResultMessage()
      } else {
        val b: Array[Int] = new Array[Int](depth + 1)
        for (i <- 0 until size) {
          System.arraycopy(a, 0, b, 0, depth)
          b(depth) = i
          if (NQueensBoxConfig.boardValid(depth + 1, b)) {
            nQueensBoxKernelSeq(b, depth + 1)
          }
        }
      }
    }

  }

}