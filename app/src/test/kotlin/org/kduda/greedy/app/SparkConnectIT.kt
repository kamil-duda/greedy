package org.kduda.greedy.app

import io.kotest.matchers.shouldBe
import org.apache.spark.sql.Row
import org.apache.spark.sql.RowFactory
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.DataTypes
import org.apache.spark.sql.types.StructType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.kduda.greedy.testing.containers.SparkConnectContainer

/** The app's Spark Connect client against a Spark Connect server: queries run, and rows travel both ways (Arrow). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SparkConnectIT {
	private val spark: SparkSession =
		SparkSession
			.builder()
			.remote(SparkConnectContainer.shared.connectUrl())
			.getOrCreate()

	@AfterAll
	fun close() = spark.close()

	@Test
	fun `a query runs on the server`() {
		spark.range(10).count() shouldBe 10L
	}

	@Test
	fun `rows sent to the server come back unchanged`() {
		val schema =
			StructType()
				.add("id", DataTypes.IntegerType)
				.add("outlook", DataTypes.StringType)
				.add("support", DataTypes.LongType)
		val rows: List<Row> =
			listOf(
				RowFactory.create(1, "sunny", 3L),
				RowFactory.create(2, "rainy", 0L),
				RowFactory.create(3, null, 7L),
			)

		val back = spark.createDataFrame(rows, schema).orderBy("id").collectAsList()

		back.map { it.values() } shouldBe rows.map { it.values() }
	}

	private fun Row.values(): List<Any?> = (0 until length()).map(::get)
}
