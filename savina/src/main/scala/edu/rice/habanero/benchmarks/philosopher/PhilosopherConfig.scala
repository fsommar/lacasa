package edu.rice.habanero.benchmarks.philosopher

import edu.rice.habanero.benchmarks.BenchmarkRunner

object PhilosopherConfig {

// num philosophers
  var N: Int = 20

// num eating rounds
  var M: Int = 10000

// num channels
  var C: Int = 1

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
          M = java.lang.Integer.parseInt(args(i))
        case "-c" =>
          i += 1
          C = java.lang.Integer.parseInt(args(i))
        case "-debug" | "-verbose" => debug = true

      }
      i += 1
    }
  }

  def printArgs(): Unit = {
    printf(BenchmarkRunner.argOutputFormat,
                      "N (num philosophers)",
                      N)
    printf(BenchmarkRunner.argOutputFormat,
                      "M (num eating rounds)",
                      M)
    printf(BenchmarkRunner.argOutputFormat, "C (num channels)", C)
    printf(BenchmarkRunner.argOutputFormat, "debug", debug)
  }

}
