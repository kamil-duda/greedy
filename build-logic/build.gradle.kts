// Convention plugins for the modules (src/main/kotlin/greedy.*.gradle.kts). Every third-party Gradle plugin the
// modules use is a dependency here, with its version from the catalog.

plugins {
	`kotlin-dsl`
}

dependencies {
	implementation(libs.kotlin.gradle.plugin)
	implementation(libs.kotlin.allopen)
	implementation(libs.spring.boot.gradle.plugin)
	implementation(libs.spotless.gradle.plugin)
	implementation(libs.scalafix.gradle.plugin)
	implementation(libs.detekt.gradle.plugin)
	implementation(libs.asm.tree)

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
	useJUnitPlatform()
}

dependencyLocking {
	lockAllConfigurations()
	lockMode = LockMode.STRICT
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
