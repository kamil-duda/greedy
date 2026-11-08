package org.kduda.greedy.build

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

/**
 * Locks every configuration of the project and registers `resolveAndLockAll` (the Gradle documentation's pattern),
 * which `make lock` runs with `--write-locks`.
 */
fun Project.lockAllDependencies() {
	dependencyLocking.lockAllConfigurations()
	dependencyLocking.lockMode.set(LockMode.STRICT)
	tasks.register("resolveAndLockAll") {
		notCompatibleWithConfigurationCache("Filters configurations at execution time")
		doFirst {
			require(gradle.startParameter.isWriteDependencyLocks) { "$path must be run with --write-locks" }
		}
		doLast {
			configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
		}
	}
}

/**
 * What every test task shares: the repository root as system property `greedy.repo.root`, test logging, and the
 * `-PtestOne` mode of `make test-one`. With `--tests <pattern>` over all modules, a module without a match must pass;
 * the recipe then checks that some module wrote a result. Up-to-date checks are off in that mode, so a repeated
 * `make test-one` runs the test again instead of finding nothing new.
 */
fun Test.applyGreedyTestDefaults(project: Project) {
	systemProperty("greedy.repo.root", project.rootDir.absolutePath)
	testLogging {
		events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
		exceptionFormat = TestExceptionFormat.FULL
	}
	if (project.providers.gradleProperty("testOne").isPresent) {
		filter.isFailOnNoMatchingTests = false
		failOnNoDiscoveredTests.set(false)
		outputs.upToDateWhen { false }
	}
}
