package examples.akka

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object SieveConfig {
  val N = 100000
  val M = 1000

  def isLocallyPrime(
          candidate: Long,
          localPrimes: Array[Long],
          start: Int,
          end: Int): Boolean = {
      for (i <- start until end) {
          if (candidate % localPrimes(i) == 0) {
              return false
          }
      }
      true
  }
}

object Sieve {

  def main(args: Array[String]) {
    val system = ActorSystem("Sieve")

    val producerActor = system.actorOf(Props(new NumberProducerActor(SieveConfig.N)))

    val filterActor = system.actorOf(Props(new PrimeFilterActor(1, 2, SieveConfig.M)))

    producerActor ! filterActor

    Thread.sleep(6000)
    system.terminate()
  }

  case class LongBox(value: Long)

  case class ExitMessage()

  private class NumberProducerActor(limit: Long) extends Actor {
    override def receive: Receive = {
      case filterActor: ActorRef =>
        for (candidate <- (3L until limit) by 2L) {
          filterActor ! LongBox(candidate)
        }
        filterActor ! ExitMessage()
        context.stop(self)
    }
  }

  private class PrimeFilterActor(val id: Int, val myInitialPrime: Long, numMaxLocalPrimes: Int) extends Actor {

    var nextFilterActor: ActorRef = null
    val localPrimes = new Array[Long](numMaxLocalPrimes)

    var availableLocalPrimes = 1
    localPrimes(0) = myInitialPrime

    private def handleNewPrime(newPrime: Long): Unit = {
      if (availableLocalPrimes < numMaxLocalPrimes) {
        localPrimes(availableLocalPrimes) = newPrime
        availableLocalPrimes += 1
      } else {
        nextFilterActor = context.system.actorOf(Props(new PrimeFilterActor(id + 1, newPrime, numMaxLocalPrimes)))
      }
    }

    override def receive: Receive = {
      try {
        {
          case candidate: LongBox =>
            val locallyPrime = SieveConfig.isLocallyPrime(candidate.value, localPrimes, 0, availableLocalPrimes)
            if (locallyPrime) {
              if (nextFilterActor != null) {
                nextFilterActor ! candidate
              } else {
                handleNewPrime(candidate.value)
              }
            }
          case x: ExitMessage =>
            if (nextFilterActor != null) {
              // Signal next actor for termination
              nextFilterActor ! x
            } else {
              val totalPrimes = ((id - 1) * numMaxLocalPrimes) + availableLocalPrimes
              println("Total primes = " + totalPrimes)
            }
            println("Terminating prime actor for number " + myInitialPrime)
            context.stop(self)
        }
      } catch {
        case e: Exception =>
          e.printStackTrace()
          PartialFunction.empty
      }
    }
  }

}