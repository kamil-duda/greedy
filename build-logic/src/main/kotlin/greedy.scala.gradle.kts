// Scala modules (spark-rules): Scala 2.13 on a JDK 21 toolchain, ScalaTest on JUnit Platform, dependency locking.

import org.kduda.greedy.build.applyGreedyTestDefaults
import org.kduda.greedy.build.lockAllDependencies

plugins {
	scala
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
	// strictly: a lock entry is itself a strict constraint, so a plain declaration below the locked version would be
	// upgraded to it in silence; this way a catalog change to Scala without `make lock` fails in either direction
	"implementation"(libs.findLibrary("scala-library").get()) {
		version {
			strictly(scalaRelease)
		}
	}
}

lockAllDependencies()

tasks.withType<ScalaCompile>().configureEach {
	scalaCompileOptions.additionalParameters =
		listOf("-deprecation", "-feature", "-unchecked", "-Xlint", "-Wunused:imports,privates,locals", "-Werror")
}

// The JDK module opens Spark's own launcher adds (JavaModuleOptions), needed by a classic in-process session
val sparkClassicJvmArgs =
	listOf(
		"java.lang", "java.lang.invoke", "java.lang.reflect", "java.io", "java.net", "java.nio", "java.util",
		"java.util.concurrent", "java.util.concurrent.atomic", "jdk.internal.ref", "sun.nio.ch", "sun.nio.cs",
		"sun.security.action", "sun.util.calendar",
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
