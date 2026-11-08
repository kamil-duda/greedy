import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.spring)
	alias(libs.plugins.spring.boot)
}

kotlin { jvmToolchain(21) }

dependencies {
	implementation(platform(SpringBootPlugin.BOM_COORDINATES))
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation(libs.postgresql)
	implementation(libs.spark.connect.client) {
		// Spark's client brings log4j as an SLF4J binding, which clashes with Spring Boot's log4j-to-slf4j;
		// log4j-core itself stays: the client references its classes directly
		exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j2-impl")
	}
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

@Suppress("UNCHECKED_CAST")
val spikeEnv = rootProject.extra["spikeEnv"] as Map<String, String>

tasks.test {
	useJUnitPlatform()
	exclude("**/*IT.class")
}

tasks.register<Test>("integrationTest") {
	description = "Tests that need the spike stack."
	group = "verification"
	useJUnitPlatform()
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	include("**/*IT.class")
	environment(spikeEnv)
	jvmArgs("--add-opens=java.base/java.nio=ALL-UNNAMED")
	testLogging { events("passed", "failed", "skipped"); showStandardStreams = false; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
