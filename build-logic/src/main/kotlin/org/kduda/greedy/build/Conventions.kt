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

/** JVM flag the Spark Connect client needs on JDK 21: Arrow allocates direct buffers through `java.nio` internals. */
const val SPARK_CONNECT_JVM_ARG = "--add-opens=java.base/java.nio=ALL-UNNAMED"

/**
 * What the integration test tasks share: the Spark Connect client's JVM flag, a place after the unit tests, and
 * Testcontainers on Colima. With `DOCKER_HOST` at Colima's socket on the host (`~/.colima/<profile>/docker.sock`),
 * Testcontainers would mount that host path into its Ryuk container, where it does not exist; inside the VM the
 * socket is `/var/run/docker.sock`. An override already in the environment wins.
 */
fun Test.applyGreedyIntegrationTestDefaults(project: Project) {
	group = "verification"
	jvmArgs(SPARK_CONNECT_JVM_ARG)
	shouldRunAfter(project.tasks.named("test"))
	val providers = project.providers
	val colima = providers.environmentVariable("DOCKER_HOST").orNull?.contains("/.colima/") == true
	if (colima && !providers.environmentVariable("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE").isPresent) {
		environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
	}
}
