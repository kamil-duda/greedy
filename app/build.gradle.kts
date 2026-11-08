// The web application (Spring Boot). Phase 2 skeleton: health endpoint only, with the PostgreSQL datasource and the
// Spark Connect client on the classpath. `test` runs without Docker; `integrationTest` runs the classes named *IT
// against PostgreSQL and Spark Connect in Testcontainers containers.

import org.kduda.greedy.build.SPARK_CONNECT_JVM_ARG
import org.kduda.greedy.build.applyGreedyIntegrationTestDefaults

plugins {
	id("greedy.kotlin")
	id("org.jetbrains.kotlin.plugin.spring")
	id("org.springframework.boot")
}

dependencies {
	implementation(platform(libs.spring.boot.dependencies))
	implementation(libs.spring.boot.starter.webmvc)
	implementation(libs.spring.boot.starter.actuator)
	implementation(libs.spring.boot.starter.jdbc)
	implementation(libs.kotlin.reflect)
	implementation(project(":spark-rules"))
	implementation(libs.spark.connect.client) {
		// The client brings log4j as an SLF4J binding, which clashes with Spring Boot's log4j-to-slf4j (Phase 1);
		// log4j-core itself stays: the client loads its classes at runtime
		exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j2-impl")
	}
	runtimeOnly(libs.postgresql)
	// developmentOnly (bootRun's classpath only) does not extend implementation, so it needs the BOM too
	developmentOnly(platform(libs.spring.boot.dependencies))
	developmentOnly(libs.spring.boot.devtools)

	testImplementation(libs.spring.boot.starter.webmvc.test) {
		// MockK replaces Mockito in this project; without it no test JVM self-attaches Mockito's agent
		exclude(group = "org.mockito")
	}
	testImplementation(libs.spring.boot.testcontainers)
	testImplementation(libs.testcontainers.postgresql)
	testImplementation(project(":test-support"))
	testImplementation(libs.kotest.assertions.core)
	testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
	exclude("**/*IT.class")
}

tasks.register<Test>("integrationTest") {
	description = "Runs the classes named *IT against PostgreSQL and Spark Connect in Testcontainers containers."
	val testSourceSet = sourceSets.test.get()
	testClassesDirs = testSourceSet.output.classesDirs
	classpath = testSourceSet.runtimeClasspath
	include("**/*IT.class")
	applyGreedyIntegrationTestDefaults(project)
}

tasks.bootRun {
	jvmArgs(SPARK_CONNECT_JVM_ARG)
}
