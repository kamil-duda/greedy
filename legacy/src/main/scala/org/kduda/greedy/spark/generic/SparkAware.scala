package org.kduda.greedy.spark.generic

import org.kduda.greedy.spark.GreedySparkInstance

trait SparkAware {
  val sparkSession = GreedySparkInstance.sparkSession
  val sc = GreedySparkInstance.sc
  val sql = GreedySparkInstance.sql
}

