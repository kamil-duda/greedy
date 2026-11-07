package org.kduda.greedy.golden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Comparison rules for golden outputs. Legacy output order is arbitrary (hash-map and hash-set iteration), so outputs
 * are compared as sorted lists of lines; the csv formats take the leading running index off each line, because it
 * follows that order, and compare only the set of indices, which legacy always numbers 0..n-1 across all rules.
 */
public final class GoldenFiles {

	private GoldenFiles() {
	}

	/**
	 * Canonical form of an output file, chosen by its name: {@code .rul} → all lines sorted; {@code -wmetadata.csv} →
	 * header line, rule lines without their index sorted, the indices, then the statistics lines verbatim; other
	 * {@code .csv} → lines without their index sorted, then the indices. The indices are one line, {@code index 0..n-1}
	 * when they run from 0 without gaps or repeats, otherwise {@code index} and the sorted list.
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
		List<String> indices = new ArrayList<>();
		for (String line : lines) {
			int comma = line.indexOf(',');
			indices.add(comma < 0 ? line : line.substring(0, comma));
			withoutIndex.add(line.substring(comma + 1));
		}
		Collections.sort(withoutIndex);
		if (!lines.isEmpty()) {
			withoutIndex.add(indexLine(indices));
		}
		return withoutIndex;
	}

	private static String indexLine(List<String> indices) {
		List<String> expected = new ArrayList<>();
		for (int i = 0; i < indices.size(); i++) {
			expected.add(String.valueOf(i));
		}
		List<String> sorted = new ArrayList<>(indices);
		sorted.sort((a, b) -> a.length() != b.length() ? a.length() - b.length() : a.compareTo(b));
		return sorted.equals(expected) ? "index 0.." + (indices.size() - 1) : "index " + String.join(",", sorted);
	}
}
