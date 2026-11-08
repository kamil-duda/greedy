package spike.jdbc

import org.apache.spark.sql.SaveMode
import org.apache.spark.sql.SparkSession
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import org.springframework.stereotype.Component
import spike.SpikeProperties
import java.io.Reader
import javax.sql.DataSource

/** The app loads rows with COPY; Spark reads them in parallel over JDBC and writes a result back, tagged with the run. */
@Component
class JdbcProbe(private val dataSource: DataSource, private val spark: SparkSession, private val props: SpikeProperties) {

	/** (Re)creates [table] as `row_no bigint primary key, a text, b text, d text` and loads [rows] generated rows. */
	fun loadRows(table: String, rows: Long): Long = dataSource.connection.use { connection ->
		connection.createStatement().use {
			it.execute("drop table if exists $table; create table $table (row_no bigint primary key, a text, b text, d text)")
		}
		CopyManager(connection.unwrap(BaseConnection::class.java))
			.copyIn("COPY $table (row_no, a, b, d) FROM STDIN (FORMAT csv)", GeneratedRows(rows))
	}

	/** Spark reads [source] in [partitions] row-number ranges at [url], groups by `d` and overwrites [target]. */
	fun roundTrip(runId: String, source: String, target: String, partitions: Int, url: String = props.db.sparkUrl): Long {
		val (low, high) = bounds(source)
		val tag = "run-$runId"
		spark.addTag(tag)
		try {
			// --packages jars are invisible to DriverManager on the Connect server: name the driver class
			spark.read().format("jdbc").option("driver", "org.postgresql.Driver").option("url", url).option("dbtable", source)
				.option("user", props.db.user).option("password", props.db.password)
				.option("partitionColumn", "row_no").option("lowerBound", low).option("upperBound", high + 1)
				.option("numPartitions", partitions.toLong())
				.load()
				.groupBy("d").count()
				.write().format("jdbc").mode(SaveMode.Overwrite).option("driver", "org.postgresql.Driver")
				.option("url", "$url?reWriteBatchedInserts=true").option("dbtable", target)
				.option("user", props.db.user).option("password", props.db.password)
				.save()
		} finally {
			spark.removeTag(tag)
		}
		return dataSource.connection.use { c -> c.createStatement().use { s -> s.executeQuery("select count(*) from $target").use { it.next(); it.getLong(1) } } }
	}

	private fun bounds(table: String): Pair<Long, Long> = dataSource.connection.use { c ->
		c.createStatement().use { s -> s.executeQuery("select min(row_no), max(row_no) from $table").use { it.next(); it.getLong(1) to it.getLong(2) } }
	}

	/** CSV rows `row_no,a,b,d` produced on demand, so loading never holds the data in memory. */
	private class GeneratedRows(private val rows: Long) : Reader() {
		private var next = 0L
		private var buffer = ""
		private var position = 0

		override fun read(target: CharArray, offset: Int, length: Int): Int {
			if (position == buffer.length) {
				if (next == rows) return -1
				val end = minOf(rows, next + 10_000)
				buffer = buildString { for (r in next until end) append(r).append(",a").append(r % 4).append(",b").append(r % 3).append(',').append("xyz"[(r % 5 % 3).toInt()]).append('\n') }
				next = end
				position = 0
			}
			val count = minOf(length, buffer.length - position)
			buffer.toCharArray(target, offset, position, position + count)
			position += count
			return count
		}

		override fun close() {}
	}
}
