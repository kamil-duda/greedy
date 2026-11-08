package spike.jdbc

import org.apache.spark.sql.SparkSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spike.support.SparkRest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CancelByTagIT(@Autowired private val spark: SparkSession) {

	@Test
	fun `interrupting a run's tag stops its job`() {
		val tag = "run-cancel"
		val job = CompletableFuture.supplyAsync {
			spark.addTag(tag) // tags apply to operations started by this thread
			try {
				runCatching { spark.range(0L, 1_000_000_000_000L).selectExpr("sum(id % 7)").collect() }.exceptionOrNull()
			} finally {
				spark.removeTag(tag)
			}
		}
		TimeUnit.SECONDS.sleep(3)
		assertThat(job.isDone).isFalse // still running: the job takes far longer than 60 s

		spark.interruptTag(tag)

		val failure = job.get(10, TimeUnit.SECONDS)
		assertThat(failure).isNotNull
		assertThat(SparkRest.jobsTagged(tag).filter { it["status"].asText() == "RUNNING" }).isEmpty()
	}
}
