package spike.prepare

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.col
import org.springframework.stereotype.Component
import spike.SpikeProperties
import java.io.InputStream
import javax.sql.DataSource

/** Candidate 3: one table per upload, `ds_<id>(row_no, c0 .. cN)`; column names stay in the dataset's record. */
@Component
class TablePerUploadPreparer(private val dataSource: DataSource, private val spark: SparkSession, private val props: SpikeProperties) : RowPreparer {
	override val candidate = "rows-table"

	override fun prepare(datasetId: Long, input: InputStream): Long {
		val records = CsvRows.stream(input).iterator()
		val columns = records.next().size
		execute(dataSource, "create table ds_$datasetId (row_no bigint primary key, ${(0 until columns).joinToString { "c$it text" }})")
		var rowNo = 0L
		val lines = records.asSequence().map { values -> "${++rowNo}," + values.joinToString(",") { csvField(it) } }
		return copyIn(dataSource, "COPY ds_$datasetId FROM STDIN (FORMAT csv)", lines)
	}

	override fun sparkRead(datasetId: Long, columns: List<String>, partitions: Int): Dataset<Row> {
		val low = queryLong(dataSource, "select coalesce(min(row_no), 0) from ds_$datasetId")
		val high = queryLong(dataSource, "select coalesce(max(row_no), 0) from ds_$datasetId")
		val rows = jdbcRead(spark, props, "ds_$datasetId", low, high, partitions)
		return rows.select(*columns.mapIndexed { i, name -> col("c$i").`as`(name) }.toTypedArray())
	}

	override fun storageBytes(datasetId: Long): Long = queryLong(dataSource, "select pg_total_relation_size('ds_$datasetId')")

	override fun drop(datasetId: Long) = execute(dataSource, "drop table if exists ds_$datasetId")
}
