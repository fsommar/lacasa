package examples.akka

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object NQueensConfig {

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

object NQueens {

  def main(args: Array[String]) {
    val numWorkers: Int = NQueensConfig.NUM_WORKERS
    val priorities: Int = NQueensConfig.PRIORITIES

    val system = ActorSystem("NQueens")

    system.actorOf(Props(new Master(numWorkers, priorities)))

    Thread.sleep(6000)
    system.terminate()

    val expSolution = NQueensConfig.SOLUTIONS(NQueensConfig.SIZE - 1)
    val actSolution = Master.resultCounter
    val solutionsLimit = NQueensConfig.SOLUTIONS_LIMIT
    val valid = actSolution >= solutionsLimit && actSolution <= expSolution
  }
  
  case class WorkMessage(priority: Int, data: Array[Int], depth: Int)

  case class DoneMessage()

  case class ResultMessage()

  case class StopMessage()

  object Master {
    var resultCounter: Long = 0
  }

  private class Master(numWorkers: Int, priorities: Int) extends Actor {

    private val solutionsLimit = NQueensConfig.SOLUTIONS_LIMIT
    private final val workers = Array.tabulate[ActorRef](numWorkers)(i => {
      context.system.actorOf(Props(new Worker(self, i)))
    })

    private var messageCounter: Int = 0
    private var numWorkersTerminated: Int = 0
    private var numWorkSent: Int = 0
    private var numWorkCompleted: Int = 0

    sendWork(
      WorkMessage(
        NQueensConfig.priority(priorities),
        new Array[Int](0),
        0))

    private def sendWork(workMessage: WorkMessage) {
      workers(messageCounter) ! workMessage
      messageCounter = (messageCounter + 1) % numWorkers
      numWorkSent += 1
    }

    override def receive: Receive = {
      case workMessage: WorkMessage =>
        sendWork(workMessage)
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

  private class Worker(master: ActorRef, id: Int) extends Actor {

    private final val threshold: Int = NQueensConfig.THRESHOLD
    private final val size: Int = NQueensConfig.SIZE

    override def receive: Receive = {
      case msg: WorkMessage if size == msg.depth || msg.depth >= threshold =>
        nqueensKernelSeq(msg.data, msg.depth)
        master ! DoneMessage()
      case msg: WorkMessage =>
         nqueensKernelPar(msg)
      case msg: StopMessage =>
        master ! msg
        context.stop(self)
    }

    def nqueensKernelPar(workMessage: WorkMessage) {
      val depth: Int = workMessage.depth
      val newPriority: Int = workMessage.priority - 1
      val newDepth: Int = depth + 1
      (0 until size)
        .map { i =>
          val newData: Array[Int] = new Array[Int](newDepth)
          System.arraycopy(workMessage.data, 0, newData, 0, depth)
          newData(depth) = i
          newData
        }
        .filter { NQueensConfig.boardValid(newDepth, _) }
        .foreach { master ! WorkMessage(NQueensConfig.priority(newPriority), _, newDepth) }

      master ! DoneMessage()
    }

    def nqueensKernelSeq(a: Array[Int], depth: Int) {
      if (size == depth) {
        master ! ResultMessage()
      } else {
        val b: Array[Int] = new Array[Int](depth + 1)
        for (i <- 0 until size) {
          System.arraycopy(a, 0, b, 0, depth)
          b(depth) = i
          if (NQueensConfig.boardValid(depth + 1, b)) {
            nqueensKernelSeq(b, depth + 1)
          }
        }
      }
    }

  }

}