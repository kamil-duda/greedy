package spike.support

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Read-only access to the Connect server's driver REST API (http://localhost:4040/api/v1). */
object SparkRest {
	private val http = HttpClient.newHttpClient()
	private val json = ObjectMapper()

	private fun get(path: String): JsonNode {
		val request = HttpRequest.newBuilder(URI.create("http://localhost:4040/api/v1$path")).GET().build()
		return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body())
	}

	private val appId: String by lazy { get("/applications")[0]["id"].asText() }

	/** Jobs whose tags contain [tag] (Connect prefixes user tags, so this matches by substring). */
	fun jobsTagged(tag: String): List<JsonNode> =
		get("/applications/$appId/jobs").filter { job -> job["jobTags"]?.any { it.asText().contains(tag) } == true }

	fun stage(stageId: Int): JsonNode = get("/applications/$appId/stages/$stageId")[0]
}
