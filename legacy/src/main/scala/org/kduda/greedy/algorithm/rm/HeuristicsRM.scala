package org.kduda.greedy.algorithm.rm

import org.apache.spark.sql.DataFrame
import org.kduda.greedy.algorithm.DecisionRulesCalculator
import org.kduda.greedy.algorithm.util.GreedyUtils
import org.kduda.greedy.spark.generic.SparkAware

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object HeuristicsRM extends DecisionRulesCalculator with SparkAware {

  override def calculateDecisionRules(dts: Map[String, DataFrame]): Map[String, List[List[(String, String)]]] = {
    dts.map {
      case (key, dt) =>
        val result = mutable.HashSet.empty[ArrayBuffer[(String, String)]]
        dt.cache()

        val ALL_COLUMNS = dt.columns
        val CONDITION_COLUMNS: ArrayBuffer[String] = ArrayBuffer(ALL_COLUMNS.dropRight(1): _*)
        val DECISION_COLUMN = key

        // if degenerated or empty return empty array
        if (dt.select(DECISION_COLUMN).distinct().count() <= 1)
          (key, List.empty[List[(String, String)]])
        else {
          val dtRowsMaster = dt.collect()
          var dtRows = dtRowsMaster

          // calculating decision rule for each row
          for (dtRow <- dtRows) {
            val decision = dtRow.getAs[String](DECISION_COLUMN)
            val conditionCols = CONDITION_COLUMNS.clone()

            var distinctDecisions = 0
            var decisionRule = ArrayBuffer((DECISION_COLUMN, dtRow.getAs[String](DECISION_COLUMN)))
            do {
              // potential columns with their RM calculated, format: (RM, column, value)
              val colsWithM = ArrayBuffer.empty[(Float, String, String)]

              // calculating RM
              for (col <- conditionCols) {
                // N(T)
                val NT = dtRows.filter(row => row.getAs[String](col) == dtRow.getAs[String](col))
                val NTCount: Float = NT.length
                // N(T, a)
                val NTA = NT.filter(row => row.getAs[String](DECISION_COLUMN) == decision)
                val NTACount: Float = NTA.length

                // RM = (N(T), - N(T, a)) / N(T)
                var RM = (NTCount - NTACount) / NTCount
                if (RM == Float.PositiveInfinity)
                  RM = 0.0F

                colsWithM += Tuple3(RM, col, dtRow.getAs[String](col))
              }
              // order by the descending of value of RM and get first item - it is the chosen column
              // minimizes RM
              val chosenCol = colsWithM.sortWith(_._1 < _._1).head

              // row result (support, value) () <- () ^ ... () ^ ()
              // head - support
              // head - decision
              // tail - condition
              decisionRule += Tuple2(chosenCol._2, chosenCol._3)

              // create separable subtable for created decision rule and check its decisions
              val conditions = decisionRule.tail
              val separableSubtable = GreedyUtils.filterByColumns(dtRows, conditions.toList)

              // prepare for next iteration
              distinctDecisions = GreedyUtils.countDistinctValuesIn(separableSubtable, DECISION_COLUMN)
              if (distinctDecisions > 1) {
                conditionCols -= chosenCol._2
                dtRows = GreedyUtils.filterByColumns(dtRows, List((chosenCol._2, chosenCol._3)))
              }

            }
            while (distinctDecisions > 1)

            dtRows = dtRowsMaster

            result += decisionRule
          }

          val resultWithSupport = result.map(rule => GreedyUtils.calculateSupport(dtRowsMaster, rule))
                                  .toList

          (key, resultWithSupport)
        }
    }
  }
}
