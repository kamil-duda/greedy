package spike.upload

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.desair.tus.server.TusFileUploadService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@Configuration
class TusConfig {
	/** tus 1.0 server; chunks land on local disk until the upload completes. Limit: 4 GiB plus a margin. */
	@Bean
	fun tusService(@Value("\${spike.upload.tus-dir}") dir: String): TusFileUploadService =
		TusFileUploadService().withStoragePath(dir).withUploadUri("/uploads").withMaxUploadSize(5L shl 30)
}

/** Resumable uploads; when the last chunk arrives, the file is streamed once into the [UploadSink]. */
@RestController
class TusUploadController(
	private val tus: TusFileUploadService,
	sinks: List<UploadSink>,
	@Value("\${spike.upload.sink}") sinkKind: String,
	private val completed: CompletedUploads,
) {
	private val sink = sinks.single { it.kind == sinkKind }

	@RequestMapping(
		value = ["/uploads", "/uploads/**"],
		method = [RequestMethod.POST, RequestMethod.PATCH, RequestMethod.HEAD, RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.GET],
	)
	fun handle(request: HttpServletRequest, response: HttpServletResponse) {
		tus.process(request, response)
		// clients that cannot send PATCH (e.g. Java's HttpURLConnection) send POST with X-HTTP-Method-Override
		if ((request.getHeader("X-HTTP-Method-Override") ?: request.method) != "PATCH") return
		val uri = request.requestURI
		val info = tus.getUploadInfo(uri) ?: return
		if (info.isUploadInProgress) return
		val uploadId = uri.substringAfterLast('/')
		tus.getUploadedBytes(uri).use { completed.record(uploadId, sink.store(uploadId, it)) }
		tus.deleteUpload(uri)
	}
}
