package org.kduda.greedy.algorithm

import org.apache.spark.sql.DataFrame

trait DecisionRulesCalculator {
  def calculateDecisionRules(dts: Map[String, DataFrame]): Map[String, List[List[(String, String)]]]
}
