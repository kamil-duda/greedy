package spike

import org.apache.spark.sql.SparkSession
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Where each side finds the services: the app uses localhost ports, Spark executors use Compose names. */
@ConfigurationProperties("spike")
data class SpikeProperties(val spark: Spark, val db: Db, val s3: S3) {
	data class Spark(val connectUrl: String = "sc://localhost:15002")
	data class Db(val appUrl: String, val sparkUrl: String, val user: String, val password: String)
	data class S3(
		val appEndpoint: String, val sparkEndpoint: String, val bucket: String,
		val accessKey: String, val secretKey: String,
	)
}

@Configuration
class SparkConfig {

	@Bean(destroyMethod = "close")
	fun sparkSession(props: SpikeProperties): SparkSession =
		SparkSession.builder().remote(props.spark.connectUrl).getOrCreate()
}
