import com.diffplug.gradle.spotless.SpotlessExtension

// The rewrite's build (PLAN.md): spark-rules (Scala), test-support and app (Kotlin), conventions in build-logic.
// The legacy Maven project in legacy/ is not part of it; it runs through `make legacy-*`.

plugins {
	// clean also deletes the root build/ (the stamp of `make test-one`)
	base
	// Puts build-logic and the plugins it depends on into the root class loader: every module then shares one copy
	// of the Kotlin Gradle plugin and applies third-party plugins by id without a version.
	id("greedy.kotlin") apply false
	id("greedy.scala") apply false
}

// Applied by id here, not in the plugins block: the plugin comes from build-logic's classpath, which the plugins
// block of this same build file does not search
apply(plugin = "com.diffplug.spotless")

val ktlintVersion =
	the<VersionCatalogsExtension>()
		.named("libs")
		.findVersion("ktlint")
		.get()
		.requiredVersion

configure<SpotlessExtension> {
	// The build scripts of the root project and of build-logic, and build-logic's Kotlin sources
	kotlinGradle {
		target("*.gradle.kts", "build-logic/*.gradle.kts", "build-logic/src/**/*.gradle.kts")
		ktlint(ktlintVersion)
	}
	kotlin {
		target("build-logic/src/**/*.kt")
		ktlint(ktlintVersion)
	}
}

// The tests of the convention plugins and of the collect guard live in build-logic, an included build
tasks.named("check") {
	dependsOn(gradle.includedBuild("build-logic").task(":test"))
}
