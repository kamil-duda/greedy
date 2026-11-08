// Test helpers shared by the spark-rules and app test suites. JUnit is compileOnly: each suite brings its own JUnit
// Platform (1.13 under ScalaTest in spark-rules, JUnit 6 in app), so nothing here pulls JUnit 6 onto spark-rules.

plugins {
	id("greedy.kotlin")
}

dependencies {
	compileOnly(platform(libs.junit.bom))
	compileOnly(libs.junit.jupiter.api)

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testImplementation(libs.junit.platform.testkit)
	testImplementation(libs.kotest.assertions.core)
	testRuntimeOnly(libs.junit.platform.launcher)
}
