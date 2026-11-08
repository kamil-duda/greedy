package org.kduda.greedy.algorithm

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import org.kduda.greedy.spark.generic.SparkAware

import scala.collection.mutable.ArrayBuffer
import org.apache.spark.sql._
import org.apache.spark.sql.catalyst.encoders.RowEncoder

object DecisionTableFactory extends SparkAware {


  /**
    * Produces k decision tables where k is number of attributes in information system.
    * The last attribute of each decision table is a decision attribute, preceding attributes are conditional attributes.
    *
    * @param is Information system to process.
    * @return ArrayBuffer containing cashed DataFrames, each of them is a decision table.
    */
  def extractDecisionTables(is: DataFrame): Array[DataFrame] = {
    val isColumns = is.columns
    val dtsColumns = isColumns.map(
      col => {
        var dtColumns = ArrayBuffer.empty[String]
        dtColumns.insertAll(0, isColumns)
        dtColumns -= col
        dtColumns += col
        dtColumns
      })

    val dts = dtsColumns.map {
      dtCols => is.select(dtCols.head, dtCols.drop(1): _*)
    }

    dts
  }

  /**
    * Maps decision tables by their decision attribute as a key.
    *
    * @param dts Decision tables (DataFrames) to be mapped.
    * @return Map[decision attribute -> decision table].
    */
  def createMapOf(dts: Array[DataFrame]): Map[String, DataFrame] = {
    dts.map { case (dt) => (dt.columns.last, dt) }.toMap
  }

  /**
    * AKA FirstIndex removing of inconsistencies method.
    * Removes all of the inconsistencies from given decision tables.
    *
    * @param dts A Map of decision tables to be filtered.
    * @return Mapped decision table (DataFrame) without duplicated conditional attributes values.
    */
  def removeDuplicatedConditions(dts: Map[String, DataFrame]): Map[String, DataFrame] = {
    dts.map {
      case (key, value) =>
        val columns = value.columns.dropRight(1)
        (key, value.dropDuplicates(columns).cache())
    }
  }

  /**
    * AKA FirstIndex removing of inconsistencies method.
    * Removes all the rows with duplicated conditional attributes from given decision tables.
    *
    * @param dts An Array of decision tables to be filtered.
    * @return Array of decision tables (DataFrames) without duplicated conditional attributes values.
    */
  def removeDuplicatedConditions(dts: Array[DataFrame]): Array[DataFrame] = {
    dts.map(dt => {
      val columns = dt.columns.dropRight(1)
      dt.dropDuplicates(columns).cache()
    })
  }

  /**
    * Removes all the rows with duplicated conditional attributes from given decision tables.
    * Most Common Decision method.
    *
    * @param dts A Map of decision tables to be filtered.
    * @return Mapped decision table (DataFrame) without inconsistencies.
    */
  def removeInconsistenciesMCD(dts: Map[String, DataFrame]): Map[String, DataFrame] = {
    dts.map {
      case (key, value) =>
        val dt = value.dropDuplicates().cache()

        val allCols = dt.columns
        val conditionCols = allCols.dropRight(1)
        val decisionColName = allCols.last

        val decisionCount = countDecisions(dt, decisionColName)
        val inconsistentConditions = extractInconsistentConditions(dt, conditionCols)
                                     .cache()

        if (inconsistentConditions.count() == 0) {
          // decision table is consistent
          inconsistentConditions.unpersist()
          (key, dt)
        }
        else {
          val inconsistentData = dt.join(broadcast(inconsistentConditions), inconsistentConditions.columns)
                                 .cache()
          val inconsistentDataCondCols = inconsistentData.columns.dropRight(1)
          inconsistentConditions.unpersist()

          val inconsistentGrouped = inconsistentData
                                    .groupBy(inconsistentDataCondCols.head, inconsistentDataCondCols.drop(1): _*)
                                    .agg(collect_set(decisionColName))
                                    .withColumnRenamed(s"collect_set($decisionColName)", decisionColName)
                                    .cache()

          val dtRowEncoder = RowEncoder(dt.schema)
          val consistent = inconsistentGrouped.map(row => {
            val set = row.getAs[Seq[String]](decisionColName)

            val mcd = decisionCount
                      .find(decision => set.contains(decision.getString(0)))
                      .get.getString(0)

            Row.fromSeq(row.toSeq.dropRight(1) :+ mcd)
          })(dtRowEncoder)
          inconsistentGrouped.unpersist()

          val result = dt.except(inconsistentData).union(consistent)
          dt.unpersist()

          (key, result.cache())
        }
    }
  }

  /**
    * Removes all of the inconsistencies from given decision tables.
    * Most Common Decision method.
    *
    * @param decisionTables An Array of decision tables to be filtered.
    * @return Array of decision tables (DataFrames) without inconsistencies.
    */
  def removeInconsistenciesMCD(decisionTables: Array[DataFrame]): Array[DataFrame] = {
    decisionTables.map(decisionTable => {
      val dt = decisionTable.dropDuplicates().cache()

      val allCols = dt.columns
      val conditionCols = allCols.dropRight(1)
      val decisionColName = allCols.last

      val decisionCount = countDecisions(dt, decisionColName)
      val inconsistentConditions = extractInconsistentConditions(dt, conditionCols)
                                   .cache()

      if (inconsistentConditions.count() == 0) {
        // decision table is consistent
        inconsistentConditions.unpersist()
        dt
      }
      else {
        val inconsistentData = dt.join(inconsistentConditions, inconsistentConditions.columns)
                               .cache()
        val inconsistentDataCondCols = inconsistentData.columns.dropRight(1)
        inconsistentConditions.unpersist()

        val inconsistentGrouped = inconsistentData
                                  .groupBy(inconsistentDataCondCols.head, inconsistentDataCondCols.drop(1): _*)
                                  .agg(collect_set(decisionColName))
                                  .withColumnRenamed(s"collect_set($decisionColName)", decisionColName)
                                  .cache()

        val dtRowEncoder = RowEncoder(dt.schema)
        val consistent = inconsistentGrouped.map(row => {
          val set = row.getAs[Seq[String]](decisionColName)

          val mcd = decisionCount
                    .find(decision => set.contains(decision.getString(0)))
                    .get.getString(0)

          Row.fromSeq(row.toSeq.dropRight(1) :+ mcd)
        })(dtRowEncoder)
        inconsistentGrouped.unpersist()

        val result = dt.except(inconsistentData).union(consistent)
        dt.unpersist()

        result.cache()
      }
    })
  }

  private def countDecisions(dt: Dataset[Row], decisionColumnName: String): Array[Row] = {
    dt.groupBy(decisionColumnName)
    .count()
    .orderBy(desc("count"))
    .drop("count")
    .collect()
  }

  private def extractInconsistentConditions(dt: Dataset[Row], conditionCols: Array[String]) = {
    dt.groupBy(conditionCols.head, conditionCols.drop(1): _*)
    .count()
    .filter(r => r.getAs[Long]("count") > 1)
    .drop("count")
  }
}
