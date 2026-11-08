package org.kduda.greedy.testing.golden

/**
 * Comparison rules for golden outputs, a port of the legacy `GoldenFiles.java`. Legacy output order is arbitrary, so
 * outputs are compared as sorted lists of lines; the csv formats take the leading running index off each line and
 * compare only the set of indices, which legacy always numbers 0..n-1 across all rules.
 */
object GoldenFiles {
	/**
	 * Canonical form of an output file, chosen by its name: `.rul` gives all lines sorted; `-wmetadata.csv` gives the
	 * header line, the rule lines without their index sorted, the indices, then the statistics lines verbatim; other
	 * `.csv` gives the lines without their index sorted, then the indices. The indices are one line, `index 0..n-1`
	 * when they run from 0 without gaps or repeats, otherwise `index` and the sorted list.
	 */
	fun normalize(
		fileName: String,
		content: String,
	): List<String> {
		val lines = content.split("\n").filter { it.isNotEmpty() }
		return when {
			fileName.endsWith(".rul") -> lines.sorted()
			fileName.endsWith("-wmetadata.csv") -> normalizeWithMetadata(lines)
			fileName.endsWith(".csv") -> sortedWithoutIndex(lines)
			else -> throw IllegalArgumentException("not a golden output file: $fileName")
		}
	}

	private fun normalizeWithMetadata(lines: List<String>): List<String> {
		if (lines.isEmpty()) return emptyList()
		val statistics = lines.indexOf("coverage").takeIf { it >= 0 } ?: lines.size
		return listOf(lines[0]) + sortedWithoutIndex(lines.subList(1, statistics)) + lines.subList(statistics, lines.size)
	}

	private fun sortedWithoutIndex(lines: List<String>): List<String> {
		val indices = lines.map { it.substringBefore(',') }
		val withoutIndex = lines.map { it.substringAfter(',', it) }.sorted()
		return if (lines.isEmpty()) withoutIndex else withoutIndex + indexLine(indices)
	}

	private fun indexLine(indices: List<String>): String {
		val expected = indices.indices.map { it.toString() }
		val sorted = indices.sortedWith(compareBy<String> { it.length }.thenBy { it })
		return if (sorted == expected) "index 0..${indices.size - 1}" else "index " + sorted.joinToString(",")
	}
}
