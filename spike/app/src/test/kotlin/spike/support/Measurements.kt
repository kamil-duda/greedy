package spike.support

import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE

/** Appends rows to spike/results/measurements.tsv (path from the `spike.results` system property). */
object Measurements {
	private const val HEADER = "candidate\tinput\tstep\tseconds\tpeak_heap_mb\tstorage_bytes\tok\tnote\n"
	private val file: Path = Path.of(System.getProperty("spike.results", "results"), "measurements.tsv")

	fun append(candidate: String, input: String, step: String, seconds: Double, peakHeapMb: Long, storageBytes: Long?, ok: Boolean, note: String = "") {
		if (Files.notExists(file)) Files.writeString(file, HEADER, CREATE)
		val row = listOf(candidate, input, step, "%.3f".format(seconds), peakHeapMb, storageBytes ?: "", ok, note.replace('\t', ' '))
		Files.writeString(file, row.joinToString("\t") + "\n", APPEND)
	}

	/** Runs [block], returning its result with the elapsed seconds and the JVM's peak heap use in MiB during it. */
	fun <T> timed(block: () -> T): Triple<T, Double, Long> {
		val pools = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }
		System.gc()
		pools.forEach { it.resetPeakUsage() }
		val start = System.nanoTime()
		val result = block()
		val seconds = (System.nanoTime() - start) / 1e9
		val peak = pools.sumOf { it.peakUsage.used } / (1024 * 1024)
		return Triple(result, seconds, peak)
	}
}
