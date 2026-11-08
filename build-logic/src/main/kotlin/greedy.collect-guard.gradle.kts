// spark-rules must not collect Spark data to the driver: scans the compiled main classes (see CollectGuard) against
// the allowlist collect-guard.txt in the module directory; `check` runs it.

import org.kduda.greedy.build.CollectGuardTask

plugins {
	scala
}

val collectGuard =
	tasks.register<CollectGuardTask>("collectGuard") {
		group = "verification"
		description = "Fails when main code collects Spark data to the driver"
		classDirectories.from(sourceSets.main.map { it.output.classesDirs })
		allowlist = layout.projectDirectory.file("collect-guard.txt")
	}

tasks.named("check") {
	dependsOn(collectGuard)
}
