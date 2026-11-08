package spike.store

import org.postgresql.PGConnection
import org.postgresql.largeobject.LargeObjectManager
import org.springframework.stereotype.Component
import spike.upload.StoredOriginal
import spike.upload.hashing
import java.io.FilterInputStream
import java.io.InputStream
import javax.sql.DataSource

/** Originals as PostgreSQL large objects, streamed in one transaction (`bytea` caps at 1 GB, large objects do not). */
@Component
class LargeObjectSink(private val dataSource: DataSource) : OriginalStore {
	override val kind = "lo"

	override fun store(uploadId: String, input: InputStream): StoredOriginal = dataSource.connection.use { c ->
		c.autoCommit = false
		val manager = c.unwrap(PGConnection::class.java).largeObjectAPI
		val oid = manager.createLO(LargeObjectManager.READWRITE)
		val (bytes, sha) = manager.open(oid, LargeObjectManager.WRITE).use { lo ->
			hashing(input) { counted -> lo.outputStream.use { counted.copyTo(it, 1 shl 20) } }
		}
		c.commit()
		StoredOriginal(kind, oid.toString(), bytes, sha)
	}

	override fun open(ref: String): InputStream {
		val c = dataSource.connection
		c.autoCommit = false
		val lo = c.unwrap(PGConnection::class.java).largeObjectAPI.open(ref.toLong(), LargeObjectManager.READ)
		return object : FilterInputStream(lo.inputStream) {
			override fun close() { super.close(); lo.close(); c.commit(); c.close() }
		}
	}

	override fun storedBytes(ref: String): Long = query("select coalesce(sum(octet_length(data)), 0) from pg_largeobject where loid = $ref")

	override fun physicalBytes(): Long = query("select pg_total_relation_size('pg_largeobject')")

	override fun delete(ref: String) {
		dataSource.connection.use { c -> c.createStatement().use { it.execute("select lo_unlink($ref)"); it.execute("vacuum pg_largeobject") } }
	}

	private fun query(sql: String): Long = dataSource.connection.use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getLong(1) } } }
}
