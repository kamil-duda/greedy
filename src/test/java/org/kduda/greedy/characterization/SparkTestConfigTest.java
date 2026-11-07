package org.kduda.greedy.characterization;

import org.apache.spark.sql.SparkSession;
import org.junit.Test;
import org.kduda.greedy.spark.GreedySparkInstance;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The legacy Spark session reads its master and shuffle partitions from system properties in tests,
 * so golden runs can vary parallelism without touching the application's own configuration.
 */
public class SparkTestConfigTest {

	@Test
	public void sessionUsesMasterAndShufflePartitionsFromSystemProperties() {
		SparkSession spark = GreedySparkInstance.sparkSession();

		assertThat(System.getProperty("greedy.spark.master")).isNotNull();
		assertThat(spark.sparkContext().master()).isEqualTo(System.getProperty("greedy.spark.master"));
		assertThat(spark.conf().get("spark.sql.shuffle.partitions")).isEqualTo(System.getProperty("spark.sql.shuffle.partitions"));
	}

	/**
	 * Spark's job and SQL listeners keep per-task metrics for the last 1000 jobs, stages and executions by default.
	 * In one long-lived test JVM (golden generation) that filled the heap and GC took over; these limits keep it flat.
	 */
	@Test
	public void sessionKeepsLittleUiHistory() {
		SparkSession spark = GreedySparkInstance.sparkSession();

		assertThat(spark.conf().get("spark.ui.enabled")).isEqualTo("false");
		assertThat(spark.conf().get("spark.ui.retainedJobs")).isEqualTo("50");
		assertThat(spark.conf().get("spark.ui.retainedStages")).isEqualTo("50");
		assertThat(spark.conf().get("spark.sql.ui.retainedExecutions")).isEqualTo("50");
	}
}
