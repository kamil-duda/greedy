package org.kduda.greedy.build

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode

/** One allowlist line: [className] is a top-level class (dotted), [call] is `Dataset.<method>` or the like. */
data class AllowEntry(
	val className: String,
	val call: String,
	val reason: String,
	val line: Int,
)

data class Finding(
	val message: String,
)

/**
 * Bytecode scan that keeps `spark-rules` from collecting Spark data to the driver: any call of a forbidden method on
 * a `Dataset` (or the one-argument `pivot`) is a finding unless the allowlist names that class and call.
 */
object CollectGuard {
	private val datasetOwners =
		setOf(
			"org/apache/spark/sql/Dataset",
			"org/apache/spark/sql/classic/Dataset",
			"org/apache/spark/sql/connect/Dataset",
		)
	private val groupedOwners =
		setOf(
			"org/apache/spark/sql/RelationalGroupedDataset",
			"org/apache/spark/sql/classic/RelationalGroupedDataset",
			"org/apache/spark/sql/connect/RelationalGroupedDataset",
		)
	private val forbiddenDatasetMethods =
		setOf("collect", "collectAsList", "toLocalIterator", "take", "takeAsList", "tail", "head", "first", "show", "reduce")

	/** @param classFiles pairs of a file name (for messages) and the bytes of a class file */
	fun scan(
		classFiles: Sequence<Pair<String, ByteArray>>,
		allowlist: List<AllowEntry>,
	): List<Finding> {
		val findings = mutableListOf<Finding>()
		val used = mutableSetOf<AllowEntry>()
		allowlist.filter { it.reason.isBlank() }.forEach {
			findings += Finding("allowlist line ${it.line}: reason missing")
		}
		for ((_, bytes) in classFiles) {
			val node = ClassNode()
			ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES)
			val topLevel = node.name.substringBefore('$').replace('/', '.')
			for (method in node.methods) {
				var line = -1
				for (insn in method.instructions) {
					if (insn is LineNumberNode) line = insn.line
					if (insn !is MethodInsnNode) continue
					val call = forbiddenCall(insn) ?: continue
					val allowed = allowlist.filter { it.className == topLevel && it.call == call }
					if (allowed.isNotEmpty()) {
						used += allowed
					} else {
						val where = if (line >= 0) "${node.sourceFile ?: "?"}:$line" else "${node.sourceFile ?: "?"}"
						findings += Finding("$topLevel ($where): $call collects data to the driver")
					}
				}
			}
		}
		allowlist.filter { it !in used }.forEach {
			findings += Finding("stale allowlist entry (line ${it.line}): ${it.className} ${it.call}")
		}
		return findings
	}

	private fun forbiddenCall(insn: MethodInsnNode): String? =
		when {
			insn.owner in datasetOwners && insn.name in forbiddenDatasetMethods -> {
				"Dataset.${insn.name}"
			}

			insn.owner in groupedOwners && insn.name == "pivot" && Type.getArgumentTypes(insn.desc).size == 1 -> {
				"RelationalGroupedDataset.pivot"
			}

			else -> {
				null
			}
		}
}

/** Lines are `<class> <Dataset|RelationalGroupedDataset>.<method>  # <reason>`; `#` lines and blank lines are skipped. */
fun parseAllowlist(text: String): List<AllowEntry> =
	text
		.lines()
		.mapIndexedNotNull { index, raw ->
			val line = raw.trim()
			if (line.isEmpty() || line.startsWith("#")) return@mapIndexedNotNull null
			val code = line.substringBefore('#').trim()
			val reason = if ('#' in line) line.substringAfter('#').trim() else ""
			val parts = code.split(Regex("\\s+"))
			require(parts.size == 2 && '.' in parts[1]) {
				"allowlist line ${index + 1}: expected '<class> <Dataset|RelationalGroupedDataset>.<method>  # <reason>'"
			}
			AllowEntry(parts[0], parts[1], reason, index + 1)
		}
