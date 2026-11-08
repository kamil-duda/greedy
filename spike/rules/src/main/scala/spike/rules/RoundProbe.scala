package spike.rules

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions.{col, count, row_number}

/** One induction round in miniature, written against the shared Spark API only (no RDDs, SparkContext or UDFs). */
object RoundProbe {

  /** Per `group`, the row with the highest `score`; ties go to the lowest `col_index`. */
  def bestPerGroup(df: DataFrame): DataFrame = {
    val byScore = Window.partitionBy("group").orderBy(col("score").desc, col("col_index").asc)
    df.withColumn("rank", row_number().over(byScore)).where(col("rank") === 1).drop("rank")
  }

  /** Caches, local-checkpoints (cutting the lineage, as each round will) and counts rows per `group`. */
  def counted(df: DataFrame): DataFrame =
    df.cache().localCheckpoint().groupBy("group").agg(count("*").as("rows"))

  /** A reliable checkpoint: needs a checkpoint directory on the side that runs the plan. */
  def reliablyCheckpointed(df: DataFrame): DataFrame = df.checkpoint()
}
