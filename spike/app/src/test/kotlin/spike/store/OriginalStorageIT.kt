package spike.store

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spike.support.Measurements
import spike.upload.hashing
import java.io.File
import java.io.OutputStream

/** The original file in a PostgreSQL large object vs in Garage over S3: store, read back, compare, measure. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OriginalStorageIT(@Autowired private val stores: List<OriginalStore>) {

	private val data = File(System.getProperty("spike.results"), "data")

	@Test
	fun `originals round-trip through both stores byte-identical`() {
		assertThat(stores.map { it.kind }).containsExactlyInAnyOrder("lo", "s3")
		for (store in stores) for (name in listOf("small.csv", "large.csv")) {
			val file = File(data, name)
			val sourceSha = file.inputStream().use { input -> hashing(input) { it.copyTo(OutputStream.nullOutputStream(), 1 shl 20) }.second }
			repeat(3) { run ->
				val before = store.physicalBytes()
				val (stored, storeSeconds, storeHeap) = Measurements.timed { file.inputStream().use { store.store("t7-$name-$run", it) } }
				val physical = store.physicalBytes() - before
				assertThat(stored.bytes).isEqualTo(file.length())
				assertThat(stored.sha256).isEqualTo(sourceSha)

				val (readSha, readSeconds, readHeap) = Measurements.timed {
					store.open(stored.ref).use { input -> hashing(input) { it.copyTo(OutputStream.nullOutputStream(), 1 shl 20) }.second }
				}
				assertThat(readSha).isEqualTo(sourceSha)

				val stats = "logical ${store.storedBytes(stored.ref)} B, physical growth $physical B; run ${run + 1}"
				Measurements.append("original-${store.kind}", name, "store", storeSeconds, storeHeap, physical, true, stats)
				Measurements.append("original-${store.kind}", name, "read back", readSeconds, readHeap, physical, true, stats)
				store.delete(stored.ref)
			}
		}
	}
}
