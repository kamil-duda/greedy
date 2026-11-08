package spike

import org.apache.spark.sql.SparkSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/** The Spring context starts with a SparkSession bean that talks to the spike's Spark Connect server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SparkConnectSmokeIT(@Autowired private val spark: SparkSession) {

	@Test
	fun `runs a job on the Connect server`() {
		assertThat(spark.range(1000).count()).isEqualTo(1000L)
	}

	@Test
	fun `server runs Spark 4_3_0`() {
		assertThat(spark.version()).isEqualTo("4.3.0")
	}
}
