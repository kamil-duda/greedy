package org.kduda.greedy.unit.algorithm;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kduda.greedy.algorithm.DecisionTableFactory;
import org.kduda.greedy.spark.reader.csv.SparkCsvReader;
import org.kduda.greedy.unit.SpringUnitTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DecisionTableFactoryTest extends SpringUnitTest {

	@Autowired private SparkCsvReader sparkCsvReader;

	private Dataset<Row> informationSystem;

	@Before
	public void setUp() throws IOException {
		ClassPathResource resource = new ClassPathResource("/files/paper-sample.csv", getClass());
		File file = new File(resource.getURL().getPath());

		Map<String, String> options = new HashMap<>();
		options.put("header", "true");

		informationSystem = sparkCsvReader.read(file, options);
	}

	@After
	public void tearDown() {
		informationSystem = null;
	}

	@Test
	public void shouldExtractDecisionTables() {
		String[][] expectedColumns = {{"f2", "f3", "f1"}, {"f1", "f3", "f2"}, {"f1", "f2", "f3"}};

		Dataset<Row>[] dts = DecisionTableFactory.extractDecisionTables(informationSystem);

		for (int i = 0; i < dts.length; i++) {
			assertThat(dts[i].count()).isEqualTo(3);
			assertThat(dts[i].columns()).containsSequence(expectedColumns[i]);
		}
	}

	@Test
	public void shouldMapDataFrame() {
		//noinspection unchecked
		Dataset<Row>[] dts = new Dataset[]{informationSystem};

		scala.collection.immutable.Map<String, Dataset<Row>> result = DecisionTableFactory.createMapOf(dts);

		assertThat(result).isNotNull();
		assertThat(result.get("f3").get().columns()).containsSequence("f1", "f2", "f3");
	}

	@Test
	public void shouldRemoveDuplicatedConditionsFromMappedDecisionTables() {
		Dataset<Row>[] dts = DecisionTableFactory.extractDecisionTables(informationSystem);

		scala.collection.immutable.Map<String, Dataset<Row>> result =
			DecisionTableFactory.removeDuplicatedConditions(DecisionTableFactory.createMapOf(dts));

		assertThat(result.get("f1").get().count()).isEqualTo(2L);
		assertThat(result.get("f2").get().count()).isEqualTo(3L);
		assertThat(result.get("f3").get().count()).isEqualTo(3L);
	}


	@Test
	public void shouldRemoveDuplicatedConditionsFromArrayOfDecisionTables() {
		Dataset<Row>[] dts = DecisionTableFactory.extractDecisionTables(informationSystem);

		Dataset<Row>[] result = DecisionTableFactory.removeDuplicatedConditions(dts);

		assertThat(result[0].count()).isEqualTo(2L);
		assertThat(result[1].count()).isEqualTo(3L);
		assertThat(result[2].count()).isEqualTo(3L);
	}
}
