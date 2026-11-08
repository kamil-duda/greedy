package org.kduda.greedy.rules.testing

import org.apache.spark.sql.SparkSession

/**
 * The Spark session the tests share, one per test JVM. System property `greedy.spark.session` picks it; `classic`
 * (also the default, e.g. when run from an IDE) is an in-process local session. The tests compile against
 * `spark-sql-api` only, so the same code runs on any session the property can select.
 */
object TestSpark {

  val mode: String = sys.props.getOrElse("greedy.spark.session", "classic")

  lazy val session: SparkSession = mode match {
    case "classic" =>
      SparkSession
        .builder()
        .master("local[2]")
        .appName("greedy-tests")
        .config("spark.sql.shuffle.partitions", "4")
        .config("spark.ui.enabled", "false")
        .getOrCreate()
    case other =>
      throw new IllegalArgumentException(s"greedy.spark.session=$other is not supported (expected: classic)")
  }
}
