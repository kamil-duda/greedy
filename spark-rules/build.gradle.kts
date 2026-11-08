// The rule-induction engine. It compiles against Spark's public API only (spark-sql-api), so it runs unchanged on a
// classic session or over Spark Connect. The same specs run twice: `test` on an in-process classic session,
// `integrationTest` over Spark Connect against a server in a Testcontainers container.

import org.kduda.greedy.build.applyGreedyIntegrationTestDefaults

plugins {
	id("greedy.scala")
	id("greedy.collect-guard")
}

// The Spark runtime of `integrationTest`: the Connect client instead of classic Spark (`testRuntimeOnly`, which
// `test` and IDE runs use). The resolvable classpath takes the test dependencies without `testRuntimeOnly`.
val connectTestRuntimeOnly = configurations.dependencyScope("connectTestRuntimeOnly")
val connectTestRuntimeClasspath =
	configurations.resolvable("connectTestRuntimeClasspath") {
		extendsFrom(configurations.testImplementation.get(), configurations.runtimeOnly.get(), connectTestRuntimeOnly.get())
		// The attributes of testRuntimeClasspath, so dependencies resolve to their JVM runtime variants
		attributes {
			val testRuntime = configurations.testRuntimeClasspath.get().attributes
			testRuntime.keySet().forEach { key ->
				@Suppress("UNCHECKED_CAST")
				attribute(key as Attribute<Any>, checkNotNull(testRuntime.getAttribute(key)))
			}
		}
	}

dependencies {
	compileOnly(libs.spark.sql.api)

	testCompileOnly(libs.spark.sql.api)
	testImplementation(project(":test-support"))
	testImplementation(libs.scalatest)
	testImplementation(libs.scalatestplus.junit5)
	testRuntimeOnly(libs.spark.sql)
	add(connectTestRuntimeOnly.name, libs.spark.connect.client)
}

tasks.withType<Test>().configureEach {
	// Keeps the ScalaTest engine's JUL INFO lines ("Starting test discovery...") out of the test output
	val julConfig = file("src/test/resources/logging.properties")
	inputs.file(julConfig).withPropertyName("julConfig").withPathSensitivity(PathSensitivity.NONE)
	systemProperty("java.util.logging.config.file", julConfig.absolutePath)
}

tasks.test {
	systemProperty("greedy.spark.session", "classic")
}

tasks.register<Test>("integrationTest") {
	description = "Runs the specs of `test` over Spark Connect, against a server in a Testcontainers container."
	val testOutput = sourceSets.test.get().output
	testClassesDirs = testOutput.classesDirs
	classpath = sourceSets.main.get().output + testOutput + connectTestRuntimeClasspath.get()
	systemProperty("greedy.spark.session", "connect")
	applyGreedyIntegrationTestDefaults(project)
}
