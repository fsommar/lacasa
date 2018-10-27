package edu.rice.habanero.benchmarks.banking

import edu.rice.habanero.benchmarks.BenchmarkRunner

object BankingConfig {

// num accounts
  var A: Int = 1000

// num transactions
  var N: Int = 50000

  var INITIAL_BALANCE: Double = java.lang.Double.MAX_VALUE / (A * N)

  var debug: Boolean = false

  def parseArgs(args: Array[String]): Unit = {
    var i: Int = 0
    while (i < args.length) {
      val loopOptionKey: String = args(i)
      loopOptionKey match {
        case "-a" =>
          i += 1
          A = java.lang.Integer.parseInt(args(i))
        case "-n" =>
          i += 1
          N = java.lang.Integer.parseInt(args(i))
        case "-debug" | "-verbose" => debug = true

      }
      i += 1
    }
    INITIAL_BALANCE = ((java.lang.Double.MAX_VALUE / (A * N)) / 1000) * 1000
  }

  def printArgs(): Unit = {
    printf(BenchmarkRunner.argOutputFormat, "A (num accounts)", A)
    printf(BenchmarkRunner.argOutputFormat,
                      "N (num transactions)",
                      N)
    printf(BenchmarkRunner.argOutputFormat,
                      "Initial Balance",
                      INITIAL_BALANCE)
    printf(BenchmarkRunner.argOutputFormat, "debug", debug)
  }

  sealed trait Message extends akka.lacasa.actor.Safe

  case class StartMessage() extends Message

  case class StopMessage() extends Message

  case class ReplyMessage() extends Message

  case class DebitMessage(val sender: AnyRef, val amount: Double)
      extends Message

  case class CreditMessage(val sender: AnyRef,
                                val amount: Double,
                                val recipient: AnyRef)
      extends Message

}