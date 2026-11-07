package org.kduda.greedy.golden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.io.FileUtils;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.Test;
import org.kduda.greedy.characterization.support.LegacyPipeline;
import org.kduda.greedy.characterization.support.LegacyPipeline.Format;
import org.kduda.greedy.characterization.support.LegacyPipeline.Heuristic;
import org.kduda.greedy.characterization.support.LegacyPipeline.Mode;
import org.kduda.greedy.spark.GreedySparkInstance;
import scala.Tuple2;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Runs the legacy pipeline over every golden input × mode × heuristic and writes all three formats plus a manifest.
 * Not part of the normal suite (the class name does not end in Test); run it with {@code make legacy-golden}.
 * <p>
 * System properties: {@code golden.inputs} (default golden/inputs), {@code golden.out} (default golden/outputs),
 * {@code golden.include} (optional regex on input ids, for partial runs).
 */
public class GoldenGenerator {

	@Test
	public void generate() throws IOException {
		File inputs = new File(System.getProperty("golden.inputs", "golden/inputs"));
		File out = new File(System.getProperty("golden.out", "golden/outputs"));
		String include = System.getProperty("golden.include");
		Pattern included = include == null || include.isEmpty() ? null : Pattern.compile(include);

		List<Map<String, Object>> cases = new ArrayList<>();
		for (File input : csvFiles(inputs)) {
			String id = inputId(inputs, input);
			if (included != null && !included.matcher(id).find()) {
				continue;
			}
			File inputOut = new File(out, id);
			FileUtils.deleteDirectory(inputOut);
			inputOut.mkdirs();
			for (Mode mode : Mode.values()) {
				for (Heuristic heuristic : Heuristic.values()) {
					cases.add(runCase(input, id, inputOut, mode, heuristic));
				}
			}
		}

		writeManifest(new File(out, "manifest.json"), cases);
		System.out.println("golden: " + cases.size() + " cases written to " + out);
	}

	private static Map<String, Object> runCase(File input, String id, File inputOut, Mode mode, Heuristic heuristic) throws IOException {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("input", id);
		result.put("mode", mode.formValue);
		result.put("heuristic", heuristic.formValue);
		long start = System.currentTimeMillis();
		try {
			scala.collection.immutable.Map<String, scala.collection.immutable.List<scala.collection.immutable.List<Tuple2<String, String>>>> rules;
			try {
				Dataset<Row> csv = LegacyPipeline.readCsv(input);
				rules = LegacyPipeline.induce(csv, mode, heuristic);
			} catch (Exception e) {
				result.put("millis", System.currentTimeMillis() - start);
				result.put("outputs", null);
				result.put("error", e.getClass().getName());
				result.put("formatErrors", new LinkedHashMap<>());
				return result;
			}

			Map<String, String> outputs = new LinkedHashMap<>();
			Map<String, String> formatErrors = new LinkedHashMap<>();
			for (Format format : Format.values()) {
				String fileName = mode.formValue + "-" + heuristic.formValue + format.suffix;
				try {
					String content = LegacyPipeline.format(rules, format);
					FileUtils.writeStringToFile(new File(inputOut, fileName), content, StandardCharsets.UTF_8);
					outputs.put(format.formValue, fileName);
				} catch (Exception e) {
					formatErrors.put(format.formValue, e.getClass().getName());
				}
			}
			result.put("millis", System.currentTimeMillis() - start);
			result.put("outputs", outputs);
			result.put("error", null);
			result.put("formatErrors", formatErrors);
			return result;
		} finally {
			GreedySparkInstance.sparkSession().catalog().clearCache();
			System.out.println("golden " + id + " " + mode.formValue + " " + heuristic.formValue + " " + result.get("millis") + "ms"
				+ (result.get("error") != null ? " error=" + result.get("error") : ""));
		}
	}

	private static void writeManifest(File manifest, List<Map<String, Object>> cases) throws IOException {
		SparkSession spark = GreedySparkInstance.sparkSession();
		Map<String, Object> generatedWith = new LinkedHashMap<>();
		generatedWith.put("sparkMaster", spark.sparkContext().master());
		generatedWith.put("shufflePartitions", spark.conf().get("spark.sql.shuffle.partitions", "200"));
		generatedWith.put("localityWait", spark.conf().get("spark.locality.wait", "3s"));
		generatedWith.put("javaVersion", System.getProperty("java.version"));
		generatedWith.put("date", Instant.now().toString());

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("generatedWith", generatedWith);
		root.put("cases", cases);
		new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(manifest, root);
	}

	private static List<File> csvFiles(File inputs) {
		List<File> files = new ArrayList<>(FileUtils.listFiles(inputs, new String[]{"csv"}, true));
		files.sort((a, b) -> inputId(inputs, a).compareTo(inputId(inputs, b)));
		return files;
	}

	static String inputId(File inputs, File input) {
		String relative = inputs.toURI().relativize(input.toURI()).getPath();
		return relative.substring(0, relative.length() - ".csv".length());
	}
}
