package edu.rice.habanero.benchmarks.sieve

import edu.rice.habanero.benchmarks.BenchmarkRunner

object SieveConfig {

  var N: Long = 100000

  var M: Int = 1000

  var debug: Boolean = false

  def parseArgs(args: Array[String]): Unit = {
    var i: Int = 0
    while (i < args.length) {
      val loopOptionKey: String = args(i)
      loopOptionKey match {
        case "-n" =>
          i += 1
          N = java.lang.Long.parseLong(args(i))
        case "-m" =>
          i += 1
          M = java.lang.Integer.parseInt(args(i))
        case "-debug" | "-verbose" => debug = true

      }
      i += 1
    }
  }

  def printArgs(): Unit = {
    printf(BenchmarkRunner.argOutputFormat, "N (input size)", N)
    printf(BenchmarkRunner.argOutputFormat, "debug", debug)
  }

  def isLocallyPrime(candidate: Long,
                               localPrimes: Array[Long],
                               startInc: Int,
                               endExc: Int): Boolean = {
    for (i <- startInc until endExc) {
      val remainder: Long = candidate % localPrimes(i)
      if (remainder == 0) {
        return false
      }
    }
    true
  }

}
