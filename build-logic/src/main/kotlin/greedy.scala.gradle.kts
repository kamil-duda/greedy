// Scala modules (spark-rules): Scala 2.13 on a JDK 21 toolchain, ScalaTest on JUnit Platform, dependency locking.

import org.kduda.greedy.build.applyGreedyTestDefaults
import org.kduda.greedy.build.lockAllDependencies

plugins {
	scala
	id("com.diffplug.spotless")
	id("io.github.cosmicsilence.scalafix")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

val libs = the<VersionCatalogsExtension>().named("libs")
val scalaRelease = libs.findVersion("scala").get().requiredVersion

scala {
	// Set rather than inferred from the classpath, so the compiler resolves through the locked scalaToolchain
	// configurations instead of an unlocked detached one
	scalaVersion = scalaRelease
}

dependencies {
	// Not strictly: Scala 2.13 is not forward binary compatible, so a dependency asking for a newer scala-library must
	// win. The lock still fails an upward catalog change made without `make lock`; a lower catalog value resolving to
	// the locked version is the lock doing its job.
	"implementation"(libs.findLibrary("scala-library").get())
}

lockAllDependencies()

spotless {
	scala {
		target("src/**/*.scala")
		scalafmt(libs.findVersion("scalafmt").get().requiredVersion).configFile(rootProject.file(".scalafmt.conf"))
	}
	kotlinGradle {
		target("*.gradle.kts")
		ktlint(libs.findVersion("ktlint").get().requiredVersion)
	}
}

scalafix {
	configFile.set(rootProject.file(".scalafix.conf"))
	semanticdb {
		autoConfigure.set(true)
		version.set(libs.findVersion("semanticdb").get().requiredVersion)
	}
}

tasks.withType<ScalaCompile>().configureEach {
	scalaCompileOptions.additionalParameters =
		listOf("-deprecation", "-feature", "-unchecked", "-Xlint", "-Wunused:imports,privates,locals", "-Werror")
}

// The JDK module opens a classic in-process local Spark session needed in Phase 1 (not Spark 4.3.0's own
// JavaModuleOptions list, which differs)
val sparkClassicJvmArgs =
	listOf(
		"java.lang",
		"java.lang.invoke",
		"java.lang.reflect",
		"java.io",
		"java.net",
		"java.nio",
		"java.util",
		"java.util.concurrent",
		"java.util.concurrent.atomic",
		"jdk.internal.ref",
		"sun.nio.ch",
		"sun.nio.cs",
		"sun.security.action",
		"sun.util.calendar",
	).map { "--add-opens=java.base/$it=ALL-UNNAMED" } +
		listOf("-Djdk.reflect.useDirectMethodHandle=false", "-Dio.netty.tryReflectionSetAccessible=true")

tasks.withType<Test>().configureEach {
	// The scalatestplus engine depends on the Jupiter engine; only ScalaTest suites run here
	useJUnitPlatform {
		includeEngines("scalatest")
	}
	maxHeapSize = "2g"
	applyGreedyTestDefaults(project)
}

tasks.named<Test>("test") {
	jvmArgs(sparkClassicJvmArgs)
}
