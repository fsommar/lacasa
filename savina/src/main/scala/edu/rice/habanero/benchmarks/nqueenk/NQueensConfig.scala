package edu.rice.habanero.benchmarks.nqueenk

import edu.rice.habanero.benchmarks.BenchmarkRunner

object NQueensConfig {

    val SOLUTIONS: Array[Long] = Array(
            1,
            0,
            0,
            2,
            10,     /* 5 */
            4,
            40,
            92,
            352,
            724,    /* 10 */
            2680,
            14200,
            73712,
            365596,
            2279184, /* 15 */
            14772512,
            95815104,
            666090624,
            4968057848L,
            39029188884L /* 20 */
    )

  private val MAX_SOLUTIONS: Int = SOLUTIONS.length

  var NUM_WORKERS: Int = 20

  var SIZE: Int = 12

  var THRESHOLD: Int = 4

  var PRIORITIES: Int = 10

  var SOLUTIONS_LIMIT: Int = 1500000

  var debug: Boolean = false

  def parseArgs(args: Array[String]): Unit = {
    var i: Int = 0
    while (i < args.length) {
      val loopOptionKey: String = args(i)
      loopOptionKey match {
        case "-n" =>
          i += 1
          SIZE = Math.max(
            1,
            Math.min(java.lang.Integer.parseInt(args(i)), MAX_SOLUTIONS))
        case "-t" =>
          i += 1
          THRESHOLD = Math.max(
            1,
            Math.min(java.lang.Integer.parseInt(args(i)), MAX_SOLUTIONS))
        case "-w" =>
          i += 1
          NUM_WORKERS = java.lang.Integer.parseInt(args(i))
        case "-s" =>
          i += 1
          SOLUTIONS_LIMIT = java.lang.Integer.parseInt(args(i))
        case "-p" =>
          i += 1
          val priority: Int = java.lang.Integer.parseInt(args(i))
          val maxPriority: Int = 30 - 1
          PRIORITIES = Math.max(1, Math.min(priority, maxPriority))
        case "-debug" | "-verbose" => debug = true

      }
      i += 1
    }
  }

  def printArgs(): Unit = {
    printf(BenchmarkRunner.argOutputFormat,
                      "Num Workers",
                      NUM_WORKERS)
    printf(BenchmarkRunner.argOutputFormat, "Size", SIZE)
    printf(BenchmarkRunner.argOutputFormat,
                      "Max Solutions",
                      SOLUTIONS_LIMIT)
    printf(BenchmarkRunner.argOutputFormat, "Threshold", THRESHOLD)
    printf(BenchmarkRunner.argOutputFormat,
                      "Priorities",
                      PRIORITIES)
    printf(BenchmarkRunner.argOutputFormat, "debug", debug)
  }

    def boardValid(n: Int, a: Array[Int]): Boolean = {
        for (i <- 0 until n) {
            val p = a(i)
            for (j <- (i + 1) until n) {
                val q = a(j)
                if (q == p || q == p - (j - i) || q == p + (j - i)) {
                    return false
                }
            }
        }
        true
    }

    
    def boardValid(n: Int, a: Vector[Int]): Boolean = {
        for (i <- 0 until n) {
            val p = a(i)
            for (j <- (i + 1) until n) {
                val q = a(j)
                if (q == p || q == p - (j - i) || q == p + (j - i)) {
                    return false
                }
            }
        }
        true
    }

    def priority(x: Int): Int =
      Math.min(PRIORITIES - 1, Math.max(0, x))

}
