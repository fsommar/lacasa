package examples.akka

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object PhilosopherConfig {
  val N = 2 // num philosophers
  val M = 10000 // num eating rounds
  val C = 1 // num channels
}

object Philosopher {

  def main(args: Array[String]) {
    val system = ActorSystem("Philosopher")

    val counter = new AtomicLong(0)


    val arbitrator = system.actorOf(Props(new ArbitratorActor(PhilosopherConfig.N)))

    val philosophers = Array.tabulate[ActorRef](PhilosopherConfig.N)(i => {
      system.actorOf(Props(new PhilosopherActor(i, PhilosopherConfig.M, counter, arbitrator)))
    })

    philosophers foreach { _ ! StartMessage() }

    Thread.sleep(6000)
    system.terminate()

    println("  Num retries: " + counter.get())
  }


  case class StartMessage()

  case class ExitMessage()

  case class HungryMessage(philosopher: ActorRef, philosopherId: Int)

  case class DoneMessage(philosopherId: Int)

  case class EatMessage()

  case class DeniedMessage()


  private class PhilosopherActor(id: Int, rounds: Int, counter: AtomicLong, arbitrator: ActorRef) extends Actor {

    private var localCounter = 0L
    private var roundsSoFar = 0

    private val myHungryMessage = HungryMessage(self, id)
    private val myDoneMessage = DoneMessage(id)

    override def receive: Receive = {
      case dm: DeniedMessage =>

        localCounter += 1
        arbitrator ! myHungryMessage

      case em: EatMessage =>

        roundsSoFar += 1
        counter.addAndGet(localCounter)

        arbitrator ! myDoneMessage
        if (roundsSoFar < rounds) {
          self ! StartMessage()
        } else {
          arbitrator ! ExitMessage()
          context.stop(self)
        }

      case sm: StartMessage =>

        arbitrator ! myHungryMessage
    }
  }

  private class ArbitratorActor(numForks: Int) extends Actor {

    private val forks = Array.tabulate(numForks)(i => new AtomicBoolean(false))
    private var numExitedPhilosophers = 0

    override def receive: Receive = {
      case hm: HungryMessage =>

        val leftFork = forks(hm.philosopherId)
        val rightFork = forks((hm.philosopherId + 1) % numForks)

        if (leftFork.get() || rightFork.get()) {
          // someone else has access to the fork
          hm.philosopher ! DeniedMessage()
        } else {
          leftFork.set(true)
          rightFork.set(true)
          hm.philosopher ! EatMessage()
        }

      case dm: DoneMessage =>

        val leftFork = forks(dm.philosopherId)
        val rightFork = forks((dm.philosopherId + 1) % numForks)
        leftFork.set(false)
        rightFork.set(false)

      case em: ExitMessage =>

        numExitedPhilosophers += 1
        if (numForks == numExitedPhilosophers) {
          context.stop(self)
        }
    }
  }

}