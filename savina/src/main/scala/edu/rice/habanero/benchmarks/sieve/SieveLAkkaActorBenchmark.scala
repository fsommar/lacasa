package edu.rice.habanero.benchmarks.sieve

import akka.lacasa.actor.{ActorRef, Props, Safe}
import edu.rice.habanero.actors.{LAkkaActor => Actor, LAkkaActorState => ActorState}
import edu.rice.habanero.benchmarks.{Benchmark, BenchmarkRunner}

/**
 *
 * @author <a href="http://shams.web.rice.edu/">Shams Imam</a> (shams@rice.edu)
 */
object SieveLAkkaActorBenchmark {

  def main(args: Array[String]) {
    BenchmarkRunner.runBenchmark(args, new SieveLAkkaActorBenchmark)
  }

  private final class SieveLAkkaActorBenchmark extends Benchmark {
    def initialize(args: Array[String]) {
      SieveConfig.parseArgs(args)
    }

    def printArgInfo() {
      SieveConfig.printArgs()
    }

    def runIteration() {

      val system = ActorState.newActorSystem("Sieve")

      val producerActor = system.actorOf(Props(new NumberProducerActor(SieveConfig.N)))
      ActorState.startActor(producerActor)

      val filterActor = system.actorOf(Props(new PrimeFilterActor(1, 2, SieveConfig.M)))
      ActorState.startActor(filterActor)

      producerActor ! filterActor

      ActorState.awaitTermination(system)
    }

    def cleanupIteration(lastIteration: Boolean, execTimeMillis: Double) {
    }
  }

  sealed trait Message extends Safe

  case class LongBox(value: Long) extends Message

  case class ExitMessage() extends Message

  private class NumberProducerActor(limit: Long) extends Actor[ActorRef] {
    override def process(filterActor: ActorRef) {
      var candidate: Long = 3
      while (candidate < limit) {
        filterActor ! LongBox(candidate)
        candidate += 2
      }
      filterActor ! ExitMessage()
      exit()
    }
  }

  private class PrimeFilterActor(val id: Int, val myInitialPrime: Long, numMaxLocalPrimes: Int) extends Actor[Message] {

    var nextFilterActor: ActorRef = null
    val localPrimes = new Array[Long](numMaxLocalPrimes)

    var availableLocalPrimes = 1
    localPrimes(0) = myInitialPrime

    private def handleNewPrime(newPrime: Long): Unit = {
      if (SieveConfig.debug)
        println("Found new prime number " + newPrime)
      if (availableLocalPrimes < numMaxLocalPrimes) {
        // Store locally if there is space
        localPrimes(availableLocalPrimes) = newPrime
        availableLocalPrimes += 1
      } else {
        // Create a new actor to store the new prime
        nextFilterActor = context.system.actorOf(Props(new PrimeFilterActor(id + 1, newPrime, numMaxLocalPrimes)))
        ActorState.startActor(nextFilterActor)
      }
    }

    override def process(msg: Message) {
      try {
        msg match {
          case candidate: LongBox =>
            val locallyPrime = SieveConfig.isLocallyPrime(candidate.value, localPrimes, 0, availableLocalPrimes)
            if (locallyPrime) {
              if (nextFilterActor != null) {
                // Pass along the chain to detect for 'primeness'
                nextFilterActor ! candidate
              } else {
                // Found a new prime!
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
            if (SieveConfig.debug)
              println("Terminating prime actor for number " + myInitialPrime)
            exit()
        }
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }
  }

}
