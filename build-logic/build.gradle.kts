// Convention plugins for the modules (src/main/kotlin/greedy.*.gradle.kts). Every third-party Gradle plugin the
// modules use is a dependency here, with its version from the catalog.

plugins {
	`kotlin-dsl`
}

dependencies {
	implementation(libs.kotlin.gradle.plugin)
	implementation(libs.kotlin.allopen)
	implementation(libs.spring.boot.gradle.plugin)
}

dependencyLocking {
	lockAllConfigurations()
}

// `make lock` (./gradlew -p build-logic resolveAndLockAll --write-locks): the Gradle documentation's pattern
tasks.register("resolveAndLockAll") {
	notCompatibleWithConfigurationCache("Filters configurations at execution time")
	doFirst {
		require(gradle.startParameter.isWriteDependencyLocks) { "$path must be run with --write-locks" }
	}
	doLast {
		configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
	}
}
