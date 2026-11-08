package org.kduda.greedy.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistency of the committed golden corpus metadata: every case that differed between the two golden runs
 * ({@code nondeterministic.json}) must be explained by a most-common-decision tie ({@code mcd-ties.json}, written by
 * {@code golden/scripts/find_mcd_ties.py} from the inputs alone).
 */
public class GoldenCorpusTest {

	private static final File OUTPUTS = new File(System.getProperty("golden.root", "golden"), "outputs");

	@Test
	public void everyNondeterministicCaseHasAnMcdTie() throws IOException {
		File ties = new File(OUTPUTS, "mcd-ties.json");
		assertThat(ties).exists();

		Set<String> tied = inputModes(new ObjectMapper().readTree(ties).get("inputModes"));
		Set<String> nondeterministic = inputModes(new ObjectMapper().readTree(new File(OUTPUTS, "nondeterministic.json")).get("cases"));

		assertThat(nondeterministic).isNotEmpty();
		assertThat(tied).containsAll(nondeterministic);
	}

	private static Set<String> inputModes(JsonNode cases) {
		Set<String> inputModes = new HashSet<>();
		for (JsonNode node : cases) {
			inputModes.add(node.get("input").asText() + " " + node.get("mode").asText());
		}
		return inputModes;
	}
}
