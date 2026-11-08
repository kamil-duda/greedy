package spike.prepare

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.col
import org.springframework.stereotype.Component
import spike.SpikeProperties
import java.io.InputStream
import javax.sql.DataSource

/** Candidate 2: every upload's rows in one table, `dataset_rows(dataset_id, row_no, vals text[])`. */
@Component
class SharedTablePreparer(private val dataSource: DataSource, private val spark: SparkSession, private val props: SpikeProperties) : RowPreparer {
	override val candidate = "rows-shared"

	init {
		execute(dataSource, "create table if not exists dataset_rows (dataset_id bigint, row_no bigint, vals text[], primary key (dataset_id, row_no))")
	}

	override fun prepare(datasetId: Long, input: InputStream): Long {
		var rowNo = 0L
		val lines = CsvRows.stream(input).drop(1).map { values -> "$datasetId,${++rowNo},${csvField(textArray(values))}" }
		return copyIn(dataSource, "COPY dataset_rows (dataset_id, row_no, vals) FROM STDIN (FORMAT csv)", lines)
	}

	override fun sparkRead(datasetId: Long, columns: List<String>, partitions: Int): Dataset<Row> {
		val low = queryLong(dataSource, "select coalesce(min(row_no), 0) from dataset_rows where dataset_id = $datasetId")
		val high = queryLong(dataSource, "select coalesce(max(row_no), 0) from dataset_rows where dataset_id = $datasetId")
		val rows = jdbcRead(spark, props, "(select row_no, vals from dataset_rows where dataset_id = $datasetId) as t", low, high, partitions)
		return rows.select(*columns.mapIndexed { i, name -> col("vals").getItem(i).`as`(name) }.toTypedArray())
	}

	override fun storageBytes(datasetId: Long): Long = queryLong(dataSource, "select pg_total_relation_size('dataset_rows')")

	override fun drop(datasetId: Long) = execute(dataSource, "delete from dataset_rows where dataset_id = $datasetId", "vacuum dataset_rows")
}
