package org.kduda.greedy.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Fails with every finding of [CollectGuard] over the class directories, one per line. */
@CacheableTask
abstract class CollectGuardTask : DefaultTask() {
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.RELATIVE)
	abstract val classDirectories: ConfigurableFileCollection

	@get:InputFile
	@get:PathSensitive(PathSensitivity.NONE)
	abstract val allowlist: RegularFileProperty

	@TaskAction
	fun check() {
		val classFiles =
			classDirectories.files
				.asSequence()
				.filter { it.isDirectory }
				.flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "class" } }
				.map { it.name to it.readBytes() }
		val entries = parseAllowlist(allowlist.get().asFile.readText())
		val findings = CollectGuard.scan(classFiles, entries)
		if (findings.isNotEmpty()) {
			throw GradleException(
				"collect guard: ${findings.size} finding(s)\n" + findings.joinToString("\n") { "  " + it.message },
			)
		}
	}
}
