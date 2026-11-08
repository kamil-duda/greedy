package spike.upload

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Where a completed upload's original goes; called once per upload. Tasks 7-9 provide real stores. */
interface UploadSink {
	fun store(uploadId: String, input: InputStream): StoredOriginal
}

data class StoredOriginal(val kind: String, val ref: String, val bytes: Long, val sha256: String)

/** Completed uploads and how often the sink ran for each, so tests can check "exactly once". */
@Component
class CompletedUploads {
	private val stored = ConcurrentHashMap<String, StoredOriginal>()
	private val calls = ConcurrentHashMap<String, Int>()

	fun record(uploadId: String, original: StoredOriginal) {
		stored[uploadId] = original
		calls.merge(uploadId, 1, Int::plus)
	}

	fun get(uploadId: String): StoredOriginal? = stored[uploadId]
	fun calls(uploadId: String): Int = calls[uploadId] ?: 0
}

/** Only hashes the stream: the default until a store is chosen with `spike.upload.sink`. */
@Component
@ConditionalOnProperty("spike.upload.sink", havingValue = "hash", matchIfMissing = true)
class HashingUploadSink : UploadSink {
	override fun store(uploadId: String, input: InputStream): StoredOriginal = hashing(input) { counted -> counted.copyTo(java.io.OutputStream.nullOutputStream(), 1 shl 20) }
		.let { (bytes, sha) -> StoredOriginal("hash", uploadId, bytes, sha) }
}

/** Runs [consume] over [input] while computing its SHA-256 and byte count. */
fun hashing(input: InputStream, consume: (InputStream) -> Unit): Pair<Long, String> {
	val digest = MessageDigest.getInstance("SHA-256")
	var bytes = 0L
	val counting = object : java.io.FilterInputStream(DigestInputStream(input, digest)) {
		override fun read(): Int = super.read().also { if (it >= 0) bytes++ }
		override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) bytes += it }
	}
	consume(counting)
	return bytes to digest.digest().joinToString("") { "%02x".format(it) }
}
