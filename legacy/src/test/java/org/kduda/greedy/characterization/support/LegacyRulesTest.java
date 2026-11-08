package org.kduda.greedy.characterization.support;

import org.junit.Test;
import scala.collection.JavaConverters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kduda.greedy.characterization.support.LegacyRules.render;
import static org.kduda.greedy.characterization.support.LegacyRules.ruleSet;

public class LegacyRulesTest {

	@Test
	public void renderThenParseRoundTrips() {
		assertThat(render(ruleSet("B=0 & A=0 => D=y [1]", "A=1 => D=n [2]")))
			.containsExactly("A=1 => D=n [2]", "B=0 & A=0 => D=y [1]");
	}

	@Test
	public void decisionAttributesIterateInFirstAppearanceOrder() {
		// the formatters number rules in map iteration order, so tests rely on it
		assertThat(JavaConverters.asJavaIterableConverter(ruleSet("A=0 => B=x [1]", "B=x => A=0 [1]").keys()).asJava())
			.containsExactly("B", "A");
	}
}
