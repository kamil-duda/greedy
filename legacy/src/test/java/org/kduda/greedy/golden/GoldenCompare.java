package org.kduda.greedy.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compares two golden runs (different Spark parallelism) case by case and writes {@code <a>/nondeterministic.json}
 * listing every case whose error or normalized output differs. Fails only when a case is missing from one run.
 * Run with {@code make legacy-golden}; system properties {@code golden.a} and {@code golden.b}.
 */
public class GoldenCompare {

	private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	@Test
	public void compare() throws IOException {
		File a = new File(System.getProperty("golden.a", System.getProperty("golden.root", "golden") + "/outputs"));
		File b = new File(System.getProperty("golden.b", "target/golden-run-b"));
		JsonNode manifestA = JSON.readTree(new File(a, "manifest.json"));
		JsonNode manifestB = JSON.readTree(new File(b, "manifest.json"));
		Map<String, JsonNode> casesA = index(manifestA);
		Map<String, JsonNode> casesB = index(manifestB);

		List<Map<String, String>> differences = new ArrayList<>();
		for (Map.Entry<String, JsonNode> entry : casesA.entrySet()) {
			JsonNode caseB = casesB.get(entry.getKey());
			if (caseB != null) {
				differences.addAll(compareCase(a, b, entry.getValue(), caseB));
			}
		}

		Map<String, Object> report = new LinkedHashMap<>();
		report.put("comparedWith", manifestB.get("generatedWith"));
		report.put("cases", differences);
		JSON.writeValue(new File(a, "nondeterministic.json"), report);
		System.out.println("golden compare: " + casesA.size() + " cases, " + differences.size() + " differences");

		assertThat(casesB.keySet()).as("cases in both runs").isEqualTo(casesA.keySet());
	}

	private static List<Map<String, String>> compareCase(File a, File b, JsonNode caseA, JsonNode caseB) throws IOException {
		List<Map<String, String>> differences = new ArrayList<>();
		String errorA = text(caseA.get("error"));
		String errorB = text(caseB.get("error"));
		if (!Objects.equals(errorA, errorB)) {
			differences.add(difference(caseA, "*", "error: " + errorA + " vs " + errorB));
			return differences;
		}
		if (errorA != null) {
			return differences;
		}
		for (String format : new String[]{"csv", "rses", "csvm"}) {
			String formatErrorA = text(caseA.get("formatErrors").get(format));
			String formatErrorB = text(caseB.get("formatErrors").get(format));
			if (!Objects.equals(formatErrorA, formatErrorB)) {
				differences.add(difference(caseA, format, "format error: " + formatErrorA + " vs " + formatErrorB));
			} else if (formatErrorA == null) {
				String fileName = caseA.get("outputs").get(format).asText();
				String input = caseA.get("input").asText();
				String contentA = FileUtils.readFileToString(new File(new File(a, input), fileName), StandardCharsets.UTF_8);
				String contentB = FileUtils.readFileToString(new File(new File(b, input), fileName), StandardCharsets.UTF_8);
				if (!GoldenFiles.normalize(fileName, contentA).equals(GoldenFiles.normalize(fileName, contentB))) {
					differences.add(difference(caseA, format, "content differs"));
				}
			}
		}
		return differences;
	}

	private static Map<String, JsonNode> index(JsonNode manifest) {
		Map<String, JsonNode> cases = new LinkedHashMap<>();
		Iterator<JsonNode> iterator = manifest.get("cases").elements();
		while (iterator.hasNext()) {
			JsonNode node = iterator.next();
			cases.put(node.get("input").asText() + " " + node.get("mode").asText() + " " + node.get("heuristic").asText(), node);
		}
		return cases;
	}

	private static Map<String, String> difference(JsonNode node, String format, String reason) {
		Map<String, String> difference = new LinkedHashMap<>();
		difference.put("input", node.get("input").asText());
		difference.put("mode", node.get("mode").asText());
		difference.put("heuristic", node.get("heuristic").asText());
		difference.put("format", format);
		difference.put("reason", reason);
		return difference;
	}

	private static String text(JsonNode node) {
		return node == null || node.isNull() ? null : node.asText();
	}
}
