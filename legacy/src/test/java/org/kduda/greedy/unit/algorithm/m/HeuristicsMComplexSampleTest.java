package org.kduda.greedy.unit.algorithm.m;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kduda.greedy.algorithm.DecisionTableFactory;
import org.kduda.greedy.algorithm.m.HeuristicsM;
import org.kduda.greedy.algorithm.parser.ToStringParser;
import org.kduda.greedy.spark.reader.csv.SparkCsvReader;
import org.kduda.greedy.unit.SpringUnitTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import scala.Tuple2;
import scala.collection.immutable.List;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class HeuristicsMComplexSampleTest extends SpringUnitTest {

	@Autowired private SparkCsvReader sparkCsvReader;

	private scala.collection.immutable.Map<String, Dataset<Row>> dtsMapped;

	@SuppressWarnings({"Duplicates", "unchecked"})
	@Before
	public void setUp() throws IOException {
		ClassPathResource resource = new ClassPathResource("/files/complex-sample.csv", getClass());
		File file = new File(resource.getURL().getPath());

		Map<String, String> options = new HashMap<>();
		options.put("header", "true");

		Dataset<Row> is = sparkCsvReader.read(file, options);
		Dataset<Row>[] isArray = new Dataset[]{is};
		dtsMapped = DecisionTableFactory.createMapOf(isArray);
	}

	@After
	public void tearDown() {
		dtsMapped = null;
	}

	@Test
	public void shouldCalculateFromComplexDataM() {
		scala.collection.immutable.Map<String, List<List<Tuple2<String, String>>>> result = HeuristicsM.calculateDecisionRules(dtsMapped);

		assertThat(result).isNotNull();
	}

	@Test
	public void shouldReturnMapWith1Elements() {
		scala.collection.immutable.Map<String, List<List<Tuple2<String, String>>>> result = HeuristicsM.calculateDecisionRules(dtsMapped);

		assertThat(result.size()).isEqualTo(1);
	}

	@Test
	public void shouldReturn6Rules() {
		scala.collection.immutable.Map<String, List<List<Tuple2<String, String>>>> result = HeuristicsM.calculateDecisionRules(dtsMapped);

		assertThat(result.get("d").get().size()).isEqualTo(6);
	}

	@Test
	public void shouldReturnValidRules() throws IOException {
		scala.collection.immutable.Map<String, List<List<Tuple2<String, String>>>> rules = HeuristicsM.calculateDecisionRules(dtsMapped);

		String actual = ToStringParser.buildStringRSES(rules);

		ClassPathResource resource = new ClassPathResource("/files/complex-sample-rules.rul", getClass());
		byte[] encoded = Files.readAllBytes(Paths.get(resource.getURI()));
		String expected = new String(encoded, StandardCharsets.UTF_8);

		assertThat(actual).isEqualTo(expected);
	}

}
