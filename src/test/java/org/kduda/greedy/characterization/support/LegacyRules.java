package org.kduda.greedy.characterization.support;

import scala.Tuple2;
import scala.collection.JavaConverters;
import scala.collection.immutable.ListMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and writes the legacy rule layout in a compact notation: {@code B=0 & A=0 => D=y [1]}.
 * <p>
 * Legacy layout (what the heuristics return and ToStringParser takes): decision attribute -> rules, each rule a list
 * {@code (support, n) :: (decisionAttribute, value) :: conditions}, conditions in the order they were chosen.
 */
public final class LegacyRules {

	private static final Pattern DECISION = Pattern.compile("(.+?)=(.*) \\[(\\d+)]");

	private LegacyRules() {
	}

	/** Every rule in notation, conditions in stored order, all decision attributes together, sorted. */
	public static List<String> render(
		scala.collection.immutable.Map<String, scala.collection.immutable.List<scala.collection.immutable.List<Tuple2<String, String>>>> rules) {
		List<String> rendered = new ArrayList<>();
		for (scala.collection.immutable.List<scala.collection.immutable.List<Tuple2<String, String>>> rulesForDecision :
			JavaConverters.mapAsJavaMapConverter(rules).asJava().values()) {
			for (scala.collection.immutable.List<Tuple2<String, String>> rule : JavaConverters.seqAsJavaListConverter(rulesForDecision).asJava()) {
				List<Tuple2<String, String>> parts = JavaConverters.seqAsJavaListConverter(rule).asJava();
				List<String> conditions = new ArrayList<>();
				for (Tuple2<String, String> condition : parts.subList(2, parts.size())) {
					conditions.add(condition._1() + "=" + condition._2());
				}
				Tuple2<String, String> decision = parts.get(1);
				rendered.add(String.join(" & ", conditions) + " => " + decision._1() + "=" + decision._2() + " [" + parts.get(0)._2() + "]");
			}
		}
		Collections.sort(rendered);
		return rendered;
	}

	/** Parses rules in notation into the legacy layout; decision attributes iterate in first-appearance order. */
	public static scala.collection.immutable.Map<String, scala.collection.immutable.List<scala.collection.immutable.List<Tuple2<String, String>>>> ruleSet(
		String... rules) {
		Map<String, List<scala.collection.immutable.List<Tuple2<String, String>>>> byDecision = new LinkedHashMap<>();
		for (String rule : rules) {
			String[] sides = rule.split(" => ", 2);
			Matcher decision = DECISION.matcher(sides[1]);
			if (!decision.matches()) {
				throw new IllegalArgumentException("not a rule: " + rule);
			}
			List<Tuple2<String, String>> parts = new ArrayList<>();
			parts.add(new Tuple2<>("support", decision.group(3)));
			parts.add(new Tuple2<>(decision.group(1), decision.group(2)));
			for (String condition : sides[0].split(" & ")) {
				String[] attributeAndValue = condition.split("=", 2);
				parts.add(new Tuple2<>(attributeAndValue[0], attributeAndValue[1]));
			}
			byDecision.computeIfAbsent(decision.group(1), key -> new ArrayList<>())
				.add(JavaConverters.asScalaBufferConverter(parts).asScala().toList());
		}

		ListMap<String, scala.collection.immutable.List<scala.collection.immutable.List<Tuple2<String, String>>>> result = new ListMap<>();
		for (Map.Entry<String, List<scala.collection.immutable.List<Tuple2<String, String>>>> entry : byDecision.entrySet()) {
			result = result.$plus(new Tuple2<>(entry.getKey(), JavaConverters.asScalaBufferConverter(entry.getValue()).asScala().toList()));
		}
		return result;
	}
}
