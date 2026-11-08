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
