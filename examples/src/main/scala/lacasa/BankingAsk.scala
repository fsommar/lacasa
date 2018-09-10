import akka.lacasa.pattern.ask

import akka.lacasa.actor.{ActorLogging, ActorSystem, ActorRef, Props, Actor, Safe}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random

import akka.util.Timeout

object BankingAsk {
  def main(args: Array[String]) {
    val system = ActorSystem("BankingAsk")

    val master: ActorRef = system.actorOf(Props(
      new Teller(
        /*BankingConfig.A*/ 1000,
        /*BankingConfig.N*/ 50000)),
      "teller")
    Thread.sleep(6000)
    system.terminate()
  }

  sealed trait Message extends Safe
  case class ReplyMsg() extends Message
  case class StopMsg() extends Message
  case class DebitMsg(sender: ActorRef, amount: Double) extends Message
  case class CreditMsg(sender: ActorRef, amount: Double, recipient: ActorRef) extends Message

  protected class Teller(numAccounts: Int, numBankings: Int) extends Actor[Message] with ActorLogging {

    private val accounts = Array.tabulate[ActorRef](numAccounts)((i) => {
        context.system.actorOf(Props(
        new Account(
          i,
          /*BankingConfig.INITIAL_BALANCE*/ Double.MaxValue / (1000 * 50000))),
        s"account_$i")
    })
    private var numCompletedBankings = 0
    private val randomGen = new Random(123456)


    log.info("init")
    for (_ <- 1 to numBankings) {
      generateWork()
    }

    override def receive: Receive = {
      case sm: ReplyMsg =>
        numCompletedBankings += 1
        if (numCompletedBankings == numBankings) {
          for (account <- accounts) {
            account ! new StopMsg()
          }
          log.info("stopping")
          context.stop(self)
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

      srcAccount ! new CreditMsg(self, amount, destAccount)
    }
  }

  protected class Account(id: Int, var balance: Double) extends Actor[Message] with ActorLogging {

    override def receive: Receive = {
      case dm: DebitMsg =>
        log.info("Received debit message with $" + dm.amount + ", balance is now $" + balance)
        balance += dm.amount
        dm.sender ! new ReplyMsg()

      case cm: CreditMsg =>
        balance -= cm.amount
        implicit val timeout = Timeout(6.seconds)
        val future = cm.recipient ? new DebitMsg(self, cm.amount)
        Await.result(future, Duration.Inf)
        cm.sender ! new ReplyMsg()

      case _: StopMsg =>
        context.stop(self)
    }
  }
}