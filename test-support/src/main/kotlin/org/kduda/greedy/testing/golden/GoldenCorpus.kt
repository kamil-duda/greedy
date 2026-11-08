package org.kduda.greedy.testing.golden

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Path

/** An input and the way it is read: `dt` (decision table) or `is` (information system). */
data class InputMode(
	val input: String,
	val mode: String,
)

/** One manifest entry: the legacy outputs of an input in a mode with a heuristic, or the exception that stopped it. */
data class GoldenCase(
	val input: String,
	val mode: String,
	val heuristic: String,
	val outputs: Map<String, String>,
	val error: String?,
	val formatErrors: Map<String, String>,
) {
	val id = "$input $mode $heuristic"
}

/** Read access to the committed golden corpus (`golden/`): inputs, legacy outputs and the manifest. */
class GoldenCorpus(
	val root: Path,
) {
	private val mapper = ObjectMapper()

	fun cases(): List<GoldenCase> =
		readTree("outputs/manifest.json").get("cases").map { node ->
			GoldenCase(
				input = node.get("input").asText(),
				mode = node.get("mode").asText(),
				heuristic = node.get("heuristic").asText(),
				outputs = stringMap(node.get("outputs")),
				error = node.get("error")?.takeUnless { it.isNull }?.asText(),
				formatErrors = stringMap(node.get("formatErrors")),
			)
		}

	/** Input modes whose legacy output depends on a most-common-decision tie (`mcd-ties.json`). */
	fun mcdTies(): Set<InputMode> = inputModes(readTree("outputs/mcd-ties.json").get("inputModes"))

	/** Input modes with a case that differed between the two golden runs (`nondeterministic.json`). */
	fun nondeterministicInputModes(): Set<InputMode> = inputModes(readTree("outputs/nondeterministic.json").get("cases"))

	fun input(id: String): Path = root.resolve("inputs").resolve("$id.csv")

	/** The output file of a case in a format (`csv`, `rses`, `csvm`), or null when the manifest lists none. */
	fun output(
		case: GoldenCase,
		format: String,
	): Path? = case.outputs[format]?.let { root.resolve("outputs").resolve(case.input).resolve(it) }

	private fun readTree(relative: String): JsonNode = mapper.readTree(root.resolve(relative).toFile())

	private fun inputModes(nodes: JsonNode): Set<InputMode> =
		nodes.map { InputMode(it.get("input").asText(), it.get("mode").asText()) }.toSet()

	private fun stringMap(node: JsonNode?): Map<String, String> =
		if (node == null || node.isNull) {
			emptyMap()
		} else {
			node.properties().associate { it.key to it.value.asText() }
		}

	companion object {
		fun fromSystemProperties(): GoldenCorpus {
			val repoRoot = checkNotNull(System.getProperty("greedy.repo.root")) { "system property greedy.repo.root is not set" }
			return GoldenCorpus(Path.of(repoRoot).resolve("golden"))
		}
	}
}
