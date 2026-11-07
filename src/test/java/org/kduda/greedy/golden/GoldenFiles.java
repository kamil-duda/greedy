package org.kduda.greedy.golden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Comparison rules for golden outputs. Legacy output order is arbitrary (hash-map and hash-set iteration), so outputs
 * are compared as sets of lines; the csv formats also drop the leading running index, which follows that order.
 */
public final class GoldenFiles {

	private GoldenFiles() {
	}

	/**
	 * Canonical form of an output file, chosen by its name: {@code .rul} → all lines sorted; {@code -wmetadata.csv} →
	 * header line, rule lines without their index sorted, then the statistics lines verbatim; other {@code .csv} →
	 * lines without their index, sorted.
	 */
	public static List<String> normalize(String fileName, String content) {
		List<String> lines = new ArrayList<>();
		for (String line : content.split("\n")) {
			if (!line.isEmpty()) {
				lines.add(line);
			}
		}

		if (fileName.endsWith(".rul")) {
			Collections.sort(lines);
			return lines;
		}
		if (fileName.endsWith("-wmetadata.csv")) {
			List<String> normalized = new ArrayList<>();
			if (lines.isEmpty()) {
				return normalized;
			}
			int statistics = lines.indexOf("coverage");
			if (statistics < 0) {
				statistics = lines.size();
			}
			normalized.add(lines.get(0));
			normalized.addAll(sortedWithoutIndex(lines.subList(1, statistics)));
			normalized.addAll(lines.subList(statistics, lines.size()));
			return normalized;
		}
		if (fileName.endsWith(".csv")) {
			return sortedWithoutIndex(lines);
		}
		throw new IllegalArgumentException("not a golden output file: " + fileName);
	}

	private static List<String> sortedWithoutIndex(List<String> lines) {
		List<String> withoutIndex = new ArrayList<>();
		for (String line : lines) {
			withoutIndex.add(line.substring(line.indexOf(',') + 1));
		}
		Collections.sort(withoutIndex);
		return withoutIndex;
	}
}
