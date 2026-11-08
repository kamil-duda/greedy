package spike.prepare

import org.apache.spark.sql.SparkSession
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spike.store.LargeObjectSink
import spike.support.Measurements
import spike.support.SparkRest
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Candidates 2 and 3: rows in PostgreSQL, read by Spark over JDBC, checked against the app's own parse. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RowCandidatesIT(
	@Autowired private val preparers: List<RowPreparer>,
	@Autowired private val originals: LargeObjectSink,
	@Autowired private val spark: SparkSession,
) {
	private val data = File(System.getProperty("spike.results"), "data")
	private val ids = AtomicLong(System.currentTimeMillis() % 1_000_000 * 1000)

	@Test
	fun `Spark reads back exactly the rows the app parsed, for every awkward file`() {
		assertThat(preparers.map { it.candidate }).containsExactlyInAnyOrder("rows-shared", "rows-table")
		val files = listOf(File(data, "small.csv")) + File(data, "awkward").listFiles()!!.sortedBy { it.name }
		for (preparer in preparers) for (file in files) {
			val parsed = file.inputStream().use { CsvRows.stream(it).toList() }
			val header = parsed.first()
			val id = ids.incrementAndGet()
			val prepared = runCatching { file.inputStream().use { preparer.prepare(id, it) } }
			if (preparer.candidate == "rows-table" && header.size > 1600) {
				// PostgreSQL allows at most 1,600 columns per table (Review Focus 5)
				assertThat(prepared.exceptionOrNull()).hasMessageContaining("1600")
				Measurements.append(preparer.candidate, "awkward/${file.name}", "prepare", 0.0, 0, null, false, "fails: ${prepared.exceptionOrNull()!!.message!!.lines().first()}")
				continue
			}
			assertThat(prepared.getOrThrow()).isEqualTo((parsed.size - 1).toLong())

			val frame = preparer.sparkRead(id, header, partitions = 2)
			assertThat(frame.columns().toList()).isEqualTo(header)
			val sparkRows = frame.collectAsList().map { row -> List(row.length()) { i -> row.getString(i) } }
			assertThat(sparkRows.sortedBy { it.joinToString("\u0001") })
				.describedAs("${preparer.candidate} ${file.name}")
				.isEqualTo(parsed.drop(1).sortedBy { it.joinToString("\u0001") })
			preparer.drop(id)
		}
	}

	@Test
	fun `the JDBC read is split into exactly the requested partitions`() {
		for (preparer in preparers) {
			val id = ids.incrementAndGet()
			val header = File(data, "small.csv").inputStream().use { preparer.prepare(id, it); CsvRows.stream(File(data, "small.csv").inputStream()).first() }
			val tag = "partitions-${preparer.candidate}-$id"
			spark.addTag(tag)
			try { preparer.sparkRead(id, header, partitions = 4).count() } finally { spark.removeTag(tag) }
			val tasks = SparkRest.jobsTagged(tag).flatMap { job -> job["stageIds"].map { SparkRest.stage(it.asInt())["numTasks"].asInt() } }
			assertThat(tasks).describedAs(preparer.candidate).contains(4)
			preparer.drop(id)
		}
	}

	@Test
	fun `1 GiB from a stored original into rows, then read by Spark`() {
		val file = File(data, "large.csv")
		val header = file.inputStream().use { CsvRows.stream(it).first() }
		val original = file.inputStream().use { originals.store("t8-large", it) }
		try {
			for (preparer in preparers) repeat(3) { run ->
				val id = ids.incrementAndGet()
				val (rows, prepSeconds, prepHeap) = Measurements.timed { originals.open(original.ref).use { preparer.prepare(id, it) } }
				assertThat(rows).isEqualTo(9_900_000L)
				val size = preparer.storageBytes(id)
				Measurements.append(preparer.candidate, "large.csv", "prepare from large object (parse + COPY)", prepSeconds, prepHeap, size, true, "run ${run + 1}")

				val frame = preparer.sparkRead(id, header, partitions = 4)
				val (count, countSeconds, countHeap) = Measurements.timed { frame.count() }
				assertThat(count).isEqualTo(9_900_000L)
				Measurements.append(preparer.candidate, "large.csv", "Spark JDBC read 4 partitions: count()", countSeconds, countHeap, size, true, "run ${run + 1}")
				val (distinct, distinctSeconds, distinctHeap) = Measurements.timed { frame.dropDuplicates().count() }
				assertThat(distinct).isPositive()
				Measurements.append(preparer.candidate, "large.csv", "Spark JDBC read 4 partitions: dropDuplicates().count()", distinctSeconds, distinctHeap, size, true, "run ${run + 1}; $distinct distinct rows")
				preparer.drop(id)
			}
		} finally {
			originals.delete(original.ref)
		}
	}

	@Test
	fun `a file with more than 1,600 columns fails only for the table-per-upload candidate`() {
		val wide = File(data, "awkward/wide-2000.csv")
		val table = preparers.single { it.candidate == "rows-table" }
		assertThat(catchThrowable { wide.inputStream().use { table.prepare(ids.incrementAndGet(), it) } }).hasMessageContaining("1600")
	}
}
