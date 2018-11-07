package edu.rice.habanero.benchmarks.banking

import akka.lacasa.actor.{ActorRef, Props}
import akka.lacasa.pattern.ask
import akka.util.Timeout
import edu.rice.habanero.actors.{LAkkaActor => Actor, LAkkaActorState => ActorState}
import edu.rice.habanero.benchmarks.banking.BankingConfig._
import edu.rice.habanero.benchmarks.{Benchmark, BenchmarkRunner, PseudoRandom}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 *
 * @author <a href="http://shams.web.rice.edu/">Shams Imam</a> (shams@rice.edu)
 */
object BankingLAkkaAwaitActorBenchmark {

  def main(args: Array[String]) {
    BenchmarkRunner.runBenchmark(args, new BankingLAkkaAwaitActorBenchmark)
  }

  private final class BankingLAkkaAwaitActorBenchmark extends Benchmark {
    def initialize(args: Array[String]) {
      BankingConfig.parseArgs(args)
    }

    def printArgInfo() {
      BankingConfig.printArgs()
    }

    def runIteration() {

      val system = ActorState.newActorSystem("Banking")

      val master = system.actorOf(Props(new Teller(BankingConfig.A, BankingConfig.N)))
      ActorState.startActor(master)
      master ! StartMessage()

      ActorState.awaitTermination(system)
    }

    def cleanupIteration(lastIteration: Boolean, execTimeMillis: Double) {
    }
  }

  protected class Teller(numAccounts: Int, numBankings: Int) extends Actor[Message] {

    private val accounts = Array.tabulate[ActorRef](numAccounts)((i) => {
      context.system.actorOf(Props(new Account(i, BankingConfig.INITIAL_BALANCE)))
    })
    private var numCompletedBankings = 0

    private val randomGen = new PseudoRandom(123456)


    protected override def onPostStart() {
      accounts.foreach(loopAccount => ActorState.startActor(loopAccount))
    }

    override def process(theMsg: Message) {
      theMsg match {

        case sm: StartMessage =>

          var m = 0
          while (m < numBankings) {
            generateWork()
            m += 1
          }

        case sm: ReplyMessage =>
          numCompletedBankings += 1
          if (numCompletedBankings == numBankings) {
            accounts.foreach(loopAccount => loopAccount ! StopMessage())
            exit()
          }
      }
    }

    def generateWork(): Unit = {
      // src is lower than dest id to ensure there is never a deadlock
      val srcAccountId = randomGen.nextInt((accounts.length / 10) * 8)
      var loopId = randomGen.nextInt(accounts.length - srcAccountId)
      if (loopId == 0) {
        loopId += 1
      }
      val destAccountId = srcAccountId + loopId

      val srcAccount = accounts(srcAccountId)
      val destAccount = accounts(destAccountId)
      val amount = Math.abs(randomGen.nextDouble()) * 1000

      val sender = self
      val cm = CreditMessage(sender, amount, destAccount)
      srcAccount ! cm
    }
  }

  protected class Account(id: Int, var balance: Double) extends Actor[Message] {

    override def process(theMsg: Message) {
      theMsg match {
        case dm: DebitMessage =>

          balance += dm.amount
          sender() ! ReplyMessage()

        case cm: CreditMessage =>

          balance -= cm.amount

          val destAccount = cm.recipient.asInstanceOf[ActorRef]

          implicit val timeout = Timeout(6.seconds)
          val future = destAccount ? DebitMessage(self, cm.amount)
          Await.result(future, Duration.Inf)

          sender() ! ReplyMessage()

        case _: StopMessage =>

          exit()
      }
    }
  }

}
