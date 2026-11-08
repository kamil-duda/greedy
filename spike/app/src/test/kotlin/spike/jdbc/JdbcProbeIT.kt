package spike.jdbc

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spike.SpikeProperties
import spike.support.Measurements
import spike.support.SparkRest

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JdbcProbeIT(@Autowired private val probe: JdbcProbe, @Autowired private val props: SpikeProperties) {

	@Test
	fun `Spark reads PostgreSQL in parallel and writes back, tagged with the run`() {
		repeat(3) { run ->
			val (loaded, loadSeconds, loadHeap) = Measurements.timed { probe.loadRows("probe_rows", 1_000_000) }
			assertThat(loaded).isEqualTo(1_000_000L)
			Measurements.append("probe", "probe_rows-1M", "app COPY load", loadSeconds, loadHeap, null, true, "run ${run + 1}")

			val (written, tripSeconds, tripHeap) = Measurements.timed { probe.roundTrip("t$run", "probe_rows", "probe_out", 4) }
			assertThat(written).isEqualTo(3L) // one row per distinct d
			Measurements.append("probe", "probe_rows-1M", "Spark JDBC read 4 partitions + group + write", tripSeconds, tripHeap, null, true, "run ${run + 1}")
		}

		val jobs = SparkRest.jobsTagged("run-t0")
		assertThat(jobs).isNotEmpty
		val stageTasks = jobs.flatMap { job -> job["stageIds"].map { SparkRest.stage(it.asInt())["numTasks"].asInt() } }
		assertThat(stageTasks).contains(4) // the partitioned JDBC scan
	}

	@Test
	fun `executors reach PostgreSQL by its Compose name, not the app's localhost address`() {
		probe.loadRows("probe_small", 1_000)
		assertThat(props.db.sparkUrl).contains("//postgres:5432/")

		val thrown = catchThrowable { probe.roundTrip("bad-url", "probe_small", "probe_small_out", 2, url = props.db.appUrl) }

		assertThat(thrown).isNotNull
		assertThat(generateSequence(thrown) { it.cause }.joinToString(" | ") { it.message ?: "" }).containsIgnoringCase("connection")
	}
}
