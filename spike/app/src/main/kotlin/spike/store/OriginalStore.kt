package spike.store

import spike.upload.UploadSink
import java.io.InputStream

/** A place for uploaded originals: write once, read back as a stream, measure, delete. */
interface OriginalStore : UploadSink {
	fun open(ref: String): InputStream
	/** Bytes the store reports for one stored original (logical size). */
	fun storedBytes(ref: String): Long
	/** Disk used by the whole store right now, to measure physical growth. */
	fun physicalBytes(): Long
	fun delete(ref: String)
}
