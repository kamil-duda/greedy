package org.kduda.greedy.algorithm.poly

import org.apache.spark.sql.DataFrame
import org.kduda.greedy.algorithm.DecisionRulesCalculator
import org.kduda.greedy.algorithm.util.GreedyUtils
import org.kduda.greedy.spark.generic.SparkAware

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object HeuristicsPoly extends DecisionRulesCalculator with SparkAware {

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
              // potential columns with their poly calculated, format: (poly, column, value)
              val colsWithM = ArrayBuffer.empty[(Float, String, String)]

              // calculating beta and alpha for poly
              for (col <- conditionCols) {
                // beta = M(T, a) - M(T(col), a)             previous - actual
                // M(T, decision) = N(T) - N(T, a)           previous
                val NT: Float = dtRows.length
                val NTA: Float = dtRows.count(row => row.getAs[String](DECISION_COLUMN) == decision)
                val MTA: Float = NT - NTA

                // M(T(col), a)   = N(T(col)) - N(T(col), a) actual
                val NTCRows = dtRows.filter(row => row.getAs[String](col) == dtRow.getAs[String](col))
                val NTC: Float = NTCRows.length
                val NTCARows = NTCRows.filter(row => row.getAs[String](DECISION_COLUMN) == decision)
                val NTCA: Float = NTCARows.length
                val MTCA = NTC - NTCA

                val beta = MTA - MTCA

                // alpha = N(T, a) - N(T(col), a)            previous - actual
                val alpha = NTA - NTCA

                val poly = beta / (alpha + 1.0F)

                colsWithM += Tuple3(poly, col, dtRow.getAs[String](col))
              }
              // order by the descending of value of poly and get first item - it is the chosen column
              // maximizes poly
              val chosenCol = colsWithM.sortWith(_._1 > _._1).head

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
