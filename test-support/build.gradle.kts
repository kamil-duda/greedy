// Test helpers shared by the spark-rules and app test suites. JUnit is compileOnly: each suite brings its own JUnit
// Platform (1.13 under ScalaTest in spark-rules, JUnit 6 in app), so nothing here pulls JUnit 6 onto spark-rules.

plugins {
	id("greedy.kotlin")
}

dependencies {
	implementation(libs.jackson.databind)
	implementation(libs.fastcsv)
	compileOnly(platform(libs.junit.bom))
	compileOnly(libs.junit.jupiter.api)

	// Kotest 6 pulls kotlin-reflect 2.2.21; the BOM keeps every Kotlin artifact at the catalog's version
	testImplementation(platform(libs.kotlin.bom))
	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testImplementation(libs.junit.platform.testkit)
	testImplementation(libs.kotest.assertions.core)
	testRuntimeOnly(libs.junit.platform.launcher)
}
