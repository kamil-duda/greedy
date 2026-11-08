package spike.prepare

import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import spike.SpikeProperties
import java.io.InputStream
import java.io.Reader
import javax.sql.DataSource

/** Candidates 2 and 3: an upload's rows in PostgreSQL, loaded with COPY and read by Spark over JDBC. */
interface RowPreparer {
	val candidate: String
	/** Parses the CSV in [input] (header first) and loads its rows under [datasetId]; returns the row count. */
	fun prepare(datasetId: Long, input: InputStream): Long
	/** The dataset as Spark reads it: one string column per CSV column, named as in the header. */
	fun sparkRead(datasetId: Long, columns: List<String>, partitions: Int): Dataset<Row>
	/** Disk used by the dataset's table(s), indexes included. */
	fun storageBytes(datasetId: Long): Long
	fun drop(datasetId: Long)
}

/** COPY ... FORMAT csv with every field quoted, so an empty cell stays an empty string instead of NULL. */
internal fun csvField(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

/** A PostgreSQL text[] literal with every element quoted and escaped. */
internal fun textArray(values: List<String>): String =
	values.joinToString(",", "{", "}") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }

internal fun copyIn(dataSource: DataSource, sql: String, lines: Sequence<String>): Long = dataSource.connection.use { c ->
	CopyManager(c.unwrap(BaseConnection::class.java)).copyIn(sql, LinesReader(lines.iterator()))
}

/** Spark's JDBC reader for [table], split into [partitions] ranges of `row_no` between [low] and [high]. */
internal fun jdbcRead(spark: SparkSession, props: SpikeProperties, table: String, low: Long, high: Long, partitions: Int): Dataset<Row> =
	spark.read().format("jdbc").option("driver", "org.postgresql.Driver").option("url", props.db.sparkUrl)
		.option("user", props.db.user).option("password", props.db.password).option("dbtable", table)
		.option("partitionColumn", "row_no").option("lowerBound", low).option("upperBound", high + 1)
		.option("numPartitions", partitions.toLong()).load()

internal fun queryLong(dataSource: DataSource, sql: String): Long =
	dataSource.connection.use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getLong(1) } } }

internal fun execute(dataSource: DataSource, vararg sql: String) =
	dataSource.connection.use { c -> c.createStatement().use { s -> sql.forEach { s.execute(it) } } }

private class LinesReader(private val lines: Iterator<String>) : Reader() {
	private var buffer = ""
	private var position = 0

	override fun read(target: CharArray, offset: Int, length: Int): Int {
		while (position == buffer.length) {
			if (!lines.hasNext()) return -1
			buffer = lines.next() + "\n"
			position = 0
		}
		val count = minOf(length, buffer.length - position)
		buffer.toCharArray(target, offset, position, position + count)
		position += count
		return count
	}

	override fun close() {}
}
