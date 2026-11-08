// Kotlin modules (app, test-support): JDK 21 toolchain, JUnit Platform, dependency locking.

import org.kduda.greedy.build.applyGreedyTestDefaults
import org.kduda.greedy.build.lockAllDependencies

plugins {
	id("org.jetbrains.kotlin.jvm")
	id("com.diffplug.spotless")
	id("io.gitlab.arturbosch.detekt")
}

kotlin {
	jvmToolchain(21)
}

lockAllDependencies()

val libs = the<VersionCatalogsExtension>().named("libs")

spotless {
	kotlin {
		target("src/**/*.kt")
		ktlint(libs.findVersion("ktlint").get().requiredVersion)
	}
	kotlinGradle {
		target("*.gradle.kts")
		ktlint(libs.findVersion("ktlint").get().requiredVersion)
	}
}

detekt {
	buildUponDefaultConfig = true
	config.setFrom(rootProject.file("config/detekt.yml"))
}

// detekt 1.23.8 refuses to run against any Kotlin version but the one it was built with
configurations.matching { it.name == "detekt" }.configureEach {
	resolutionStrategy.eachDependency {
		if (requested.group == "org.jetbrains.kotlin") {
			useVersion("2.0.21")
		}
	}
}

tasks.withType<Test>().configureEach {
	// `fixture`: classes that tests run through EngineTestKit, never on their own
	useJUnitPlatform {
		excludeTags("fixture")
	}
	applyGreedyTestDefaults(project)
}
