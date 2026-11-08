// The web application (Spring Boot). Phase 2 skeleton: health endpoint only.

plugins {
	id("greedy.kotlin")
	id("org.jetbrains.kotlin.plugin.spring")
	id("org.springframework.boot")
}

dependencies {
	implementation(platform(libs.spring.boot.dependencies))
	implementation(libs.spring.boot.starter.webmvc)
	implementation(libs.spring.boot.starter.actuator)
	implementation(libs.kotlin.reflect)

	testImplementation(libs.spring.boot.starter.webmvc.test)
	testImplementation(libs.kotest.assertions.core)
	testRuntimeOnly(libs.junit.platform.launcher)
}
