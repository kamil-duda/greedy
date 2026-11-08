// Kotlin modules (app, test-support): JDK 21 toolchain, JUnit Platform, dependency locking.

import org.kduda.greedy.build.applyGreedyTestDefaults
import org.kduda.greedy.build.lockAllDependencies

plugins {
	id("org.jetbrains.kotlin.jvm")
}

kotlin {
	jvmToolchain(21)
}

lockAllDependencies()

tasks.withType<Test>().configureEach {
	// `fixture`: classes that tests run through EngineTestKit, never on their own
	useJUnitPlatform {
		excludeTags("fixture")
	}
	applyGreedyTestDefaults(project)
}
