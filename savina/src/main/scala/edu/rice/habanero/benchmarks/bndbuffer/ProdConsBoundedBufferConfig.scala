package edu.rice.habanero.benchmarks.bndbuffer

import edu.rice.habanero.benchmarks.BenchmarkRunner

import edu.rice.habanero.benchmarks.PseudoRandom

object ProdConsBoundedBufferConfig {

  var bufferSize: Int = 50

  var numProducers: Int = 40

  var numConsumers: Int = 40

  var numItemsPerProducer: Int = 1000

  var prodCost: Int = 25

  var consCost: Int = 25

  var numMailboxes: Int = 1

  var debug: Boolean = false

  def parseArgs(args: Array[String]): Unit = {
    var i: Int = 0
    while (i < args.length) {
      val loopOptionKey: String = args(i)
      loopOptionKey match {
        case "-bb" =>
          i += 1
          bufferSize = java.lang.Integer.parseInt(args(i))
        case "-np" =>
          i += 1
          numProducers = java.lang.Integer.parseInt(args(i))
        case "-nc" =>
          i += 1
          numConsumers = java.lang.Integer.parseInt(args(i))
        case "-pc" =>
          i += 1
          prodCost = java.lang.Integer.parseInt(args(i))
        case "-cc" =>
          i += 1
          consCost = java.lang.Integer.parseInt(args(i))
        case "-ipp" =>
          i += 1
          numItemsPerProducer = java.lang.Integer.parseInt(args(i))
        case "-numChannels" | "-numMailboxes" | "-nm" =>
          i += 1
          numMailboxes = java.lang.Integer.parseInt(args(i))
        case "-debug" | "-verbose" => debug = true

      }
      i += 1
    }
  }

  def printArgs(): Unit = {
    printf(BenchmarkRunner.argOutputFormat,
                      "Buffer size",
                      bufferSize)
    printf(BenchmarkRunner.argOutputFormat,
                      "num producers",
                      numProducers)
    printf(BenchmarkRunner.argOutputFormat,
                      "num consumers",
                      numConsumers)
    printf(BenchmarkRunner.argOutputFormat, "prod cost", prodCost)
    printf(BenchmarkRunner.argOutputFormat, "cons cost", consCost)
    printf(BenchmarkRunner.argOutputFormat,
                      "items per producer",
                      numItemsPerProducer)
    printf(BenchmarkRunner.argOutputFormat,
                      "num mailboxes",
                      numMailboxes)
    printf(BenchmarkRunner.argOutputFormat, "debug", debug)
  }

  def processItem(curTerm: Double, cost: Int): Double = {
    var res: Double = curTerm
    val random: PseudoRandom = new PseudoRandom(cost)
    if (cost > 0) {
      for (i <- 0 until cost; j <- 0.until(100)) {
        res += Math.log(Math.abs(random.nextDouble()) + 0.01)
      }
    } else {
      res += Math.log(Math.abs(random.nextDouble()) + 0.01)
    }
    res
  }

  sealed trait Message extends akka.lacasa.actor.Safe

  case class DataItemMessage(val data: Double, val producer: AnyRef) extends Message

  case class ProduceDataMessage() extends Message

  case class ProducerExitMessage() extends Message

  case class ConsumerAvailableMessage(val consumer: AnyRef) extends Message

  case class ConsumerExitMessage() extends Message

}
