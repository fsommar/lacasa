package edu.rice.habanero.benchmarks.bitonicsort

import edu.rice.habanero.benchmarks.BenchmarkRunner

object BitonicSortConfig {

// data size, must be power of 2
  var N: Int = 4096

// max value
  var M: Long = 1L << 60

// seed for random number generator
  var S: Long = 2048

  var debug: Boolean = false

  def parseArgs(args: Array[String]): Unit = {
    var i: Int = 0
    while (i < args.length) {
      val loopOptionKey: String = args(i)
      loopOptionKey match {
        case "-n" =>
          i += 1
          N = java.lang.Integer.parseInt(args(i))
        case "-m" =>
          i += 1
          M = java.lang.Long.parseLong(args(i))
        case "-s" =>
          i += 1
          S = java.lang.Long.parseLong(args(i))
        case "-debug" | "-verbose" => debug = true

      }
      i += 1
    }
  }

  def printArgs(): Unit = {
    printf(BenchmarkRunner.argOutputFormat, "N (num values)", N)
    printf(BenchmarkRunner.argOutputFormat, "M (max value)", M)
    printf(BenchmarkRunner.argOutputFormat, "S (seed)", S)
    printf(BenchmarkRunner.argOutputFormat, "debug", debug)
  }

}
