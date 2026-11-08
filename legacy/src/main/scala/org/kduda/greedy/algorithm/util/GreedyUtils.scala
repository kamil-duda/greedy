package org.kduda.greedy.algorithm.util

import org.apache.spark.sql.Row

import scala.collection.mutable.ArrayBuffer

object GreedyUtils {

  /**
    * Constant used in decision rule as a key pointing to the rule's support value.
    */
  val SUPPORT_ATTR_NAME: String = "support"

  /**
    * Filters rows' columns so they only contain values passed in columns parameter.
    *
    * @param rows    Data to be filtered.
    * @param columns Columns and their values in format Tuple2(column, value)
    * @return Filtered rows.
    */
  def filterByColumns(rows: Array[Row], columns: List[(String, String)]): Array[Row] = {
    var result = rows
    columns.foreach(condition => {
      result = result.filter(row => row.getAs[String](condition._1) == condition._2)
    })
    result
  }

  /**
    * Counts the number of partitions created per passed column.
    *
    * @param dtFilteredRows Data as rows.
    * @param columnName     Name of the column that should data be partitioned by.
    * @return Number of partitions (groups) per column.
    */
  def countDistinctValuesIn(dtFilteredRows: Array[Row], columnName: String): Int = {
    dtFilteredRows
    .groupBy(row => row.getAs[String](columnName))
    .keys
    .size
  }

  /**
    * Calculates support for the given decision rule.
    *
    * @param dtRows       Decision table with transactions to be checked for support.
    * @param decisionRule The decision rule to be checked.
    * @return Same decision rule with appended support so it looks like: (support, [value])(decision, [value])[tail as conditions]
    */
  def calculateSupport(dtRows: Array[Row], decisionRule: ArrayBuffer[(String, String)]): List[(String, String)] = {
    var rowsForSupport = dtRows
    for (pair <- decisionRule) {
      rowsForSupport = rowsForSupport.filter(row => row.getAs[String](pair._1) == pair._2)
    }
    decisionRule.prepend((SUPPORT_ATTR_NAME, rowsForSupport.length.toString))

    decisionRule.toList
  }
}
