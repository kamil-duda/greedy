package spike.store

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CompletedPart
import spike.SpikeProperties
import spike.upload.StoredOriginal
import spike.upload.hashing
import java.io.InputStream
import java.net.URI

@Configuration
class S3Config {
	/** The app's S3 client for Garage (path-style, its own endpoint; Spark has its own). */
	@Bean(destroyMethod = "close")
	fun s3(props: SpikeProperties): S3Client = S3Client.builder()
		.endpointOverride(URI.create(props.s3.appEndpoint))
		.region(Region.of("garage"))
		.forcePathStyle(true)
		// SDK 2.30+ adds flexible checksums by default; Garage rejects them ("Invalid payload signature")
		.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
		.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
		.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(props.s3.accessKey, props.s3.secretKey)))
		.build()
}

/** Originals as objects in the `uploads` bucket, written as a streamed multipart upload with 8 MiB parts. */
@Component
class ObjectStoreSink(private val s3: S3Client, private val props: SpikeProperties) : OriginalStore {
	override val kind = "s3"
	private val bucket get() = props.s3.bucket
	private val partSize = 8 * 1024 * 1024

	override fun store(uploadId: String, input: InputStream): StoredOriginal {
		val key = "originals/$uploadId"
		val multipart = s3.createMultipartUpload { it.bucket(bucket).key(key) }.uploadId()
		val parts = mutableListOf<CompletedPart>()
		val (bytes, sha) = hashing(input) { counted ->
			val buffer = ByteArray(partSize)
			while (true) {
				val n = counted.readNBytes(buffer, 0, partSize)
				if (n == 0) break
				val number = parts.size + 1
				val etag = s3.uploadPart({ it.bucket(bucket).key(key).uploadId(multipart).partNumber(number) }, RequestBody.fromBytes(buffer.copyOf(n))).eTag()
				parts += CompletedPart.builder().partNumber(number).eTag(etag).build()
			}
		}
		s3.completeMultipartUpload { it.bucket(bucket).key(key).uploadId(multipart).multipartUpload { m -> m.parts(parts) } }
		return StoredOriginal(kind, key, bytes, sha)
	}

	override fun open(ref: String): InputStream = s3.getObject { it.bucket(bucket).key(ref) }

	override fun storedBytes(ref: String): Long = s3.headObject { it.bucket(bucket).key(ref) }.contentLength()

	/** Garage's data volume (blocks are compressed and deduplicated); measured from a throwaway container, as Garage's image has no `du`. */
	override fun physicalBytes(): Long = ProcessBuilder("docker", "run", "--rm", "-v", "greedy-spike_garage-data:/d:ro", "--entrypoint", "du",
		"apache/spark:4.3.0-scala2.13-java21-ubuntu", "-sb", "/d")
		.start().inputStream.bufferedReader().readText().trim().substringBefore('\t').toLong()

	override fun delete(ref: String) {
		s3.deleteObject { it.bucket(bucket).key(ref) }
	}
}
