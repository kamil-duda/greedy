package spike.upload

import io.tus.java.client.TusClient
import io.tus.java.client.TusURLMemoryStore
import io.tus.java.client.TusUpload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import spike.support.Measurements
import java.io.File
import java.io.RandomAccessFile
import java.net.Socket
import java.net.URL
import java.security.MessageDigest

/** A 1 GiB upload through tus, with the connection dropped mid-chunk at ~50 % and ~99 %, in a 256 MiB heap. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResumableUploadIT(@Autowired private val completed: CompletedUploads) {

	@LocalServerPort private var port: Int = 0
	private val chunk = 8 * 1024 * 1024
	private val file = File(System.getProperty("spike.results"), "data/large.csv")

	@Test
	fun `a dropped upload resumes and arrives byte-identical`() {
		val (uploadId, seconds, peakHeap) = Measurements.timed { uploadWithTwoDrops() }

		val stored = completed.get(uploadId)!!
		assertThat(stored.bytes).isEqualTo(file.length())
		assertThat(stored.sha256).isEqualTo(sha256(file))
		Measurements.append("upload", "large.csv", "tus upload 8 MiB chunks, 2 drops", seconds, peakHeap, file.length(), true, "sink=${stored.kind}")

		// a client re-sending the final chunk must not change or re-store anything
		val resend = rawPatch(uploadId, file.length() - chunk, chunk, sendBytes = chunk)
		assertThat(resend).isIn(404, 409, 410)
		assertThat(completed.calls(uploadId)).isEqualTo(1)
	}

	private fun uploadWithTwoDrops(): String {
		val client = TusClient().apply {
			uploadCreationURL = URL("http://localhost:$port/uploads")
			enableResuming(TusURLMemoryStore())
		}
		var uploadUrl = ""
		for (dropAt in listOf(0.5, 0.99)) {
			// a fresh TusUpload per attempt: finish() closes its stream; the same fingerprint resumes the same upload
			val uploader = client.resumeOrCreateUpload(TusUpload(file)).apply { chunkSize = chunk }
			uploadUrl = uploader.uploadURL.toString()
			while (uploader.offset + chunk < file.length() * dropAt) uploader.uploadChunk()
			val offset = uploader.offset
			uploader.finish() // closes the client's connection cleanly; the drop below is the raw one
			rawPatch(uploadUrl.substringAfterLast('/'), offset, chunk, sendBytes = chunk / 2)
		}
		val uploader = client.resumeOrCreateUpload(TusUpload(file)).apply { chunkSize = chunk }
		while (uploader.uploadChunk() > -1) Unit
		uploader.finish()
		return uploadUrl.substringAfterLast('/')
	}

	/** Sends a PATCH announcing [length] bytes at [offset] but writes only [sendBytes], then hangs up. Returns the status, or -1. */
	private fun rawPatch(uploadId: String, offset: Long, length: Int, sendBytes: Int): Int = Socket("localhost", port).use { socket ->
		val out = socket.getOutputStream()
		out.write(("PATCH /uploads/$uploadId HTTP/1.1\r\nHost: localhost:$port\r\nTus-Resumable: 1.0.0\r\n" +
			"Upload-Offset: $offset\r\nContent-Type: application/offset+octet-stream\r\nContent-Length: $length\r\n\r\n").toByteArray())
		val bytes = ByteArray(sendBytes)
		RandomAccessFile(file, "r").use { it.seek(offset); it.readFully(bytes, 0, minOf(sendBytes.toLong(), file.length() - offset).toInt()) }
		runCatching { out.write(bytes); out.flush() }
		if (sendBytes < length) return -1
		val status = socket.getInputStream().bufferedReader().readLine() ?: return -1
		status.split(' ')[1].toInt()
	}

	private fun sha256(f: File): String = f.inputStream().use { input ->
		val digest = MessageDigest.getInstance("SHA-256")
		val buffer = ByteArray(1 shl 20)
		while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
		digest.digest().joinToString("") { "%02x".format(it) }
	}
}
