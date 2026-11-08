// The rule-induction engine. It compiles against Spark's public API only (spark-sql-api), so it runs unchanged on a
// classic session or over Spark Connect; the tests use an in-process classic session.

plugins {
	id("greedy.scala")
}

dependencies {
	compileOnly(libs.spark.sql.api)

	testCompileOnly(libs.spark.sql.api)
	testImplementation(libs.scalatest)
	testImplementation(libs.scalatestplus.junit5)
	testRuntimeOnly(libs.spark.sql)
}

tasks.test {
	systemProperty("greedy.spark.session", "classic")
}
