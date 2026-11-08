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

public class MostCommonDecisionTest extends SpringUnitTest {

	@Autowired private SparkCsvReader sparkCsvReader;

	private Dataset<Row> informationSystem;

	@Before
	public void setUp() throws IOException {
		ClassPathResource resource = new ClassPathResource("/files/inconsistencies.csv", getClass());
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
	public void shouldRemoveInconsistenciesFromMappedDecisionTables() {
		Dataset<Row>[] dts = DecisionTableFactory.extractDecisionTables(informationSystem);

		scala.collection.immutable.Map<String, Dataset<Row>> result =
			DecisionTableFactory.removeInconsistenciesMCD(DecisionTableFactory.createMapOf(dts));

		assertThat(result.size()).isEqualTo(4);

		assertThat(result.get("f1").get().count()).isEqualTo(6L);
		assertThat(result.get("f2").get().count()).isEqualTo(7L);
		assertThat(result.get("f3").get().count()).isEqualTo(9L);
		assertThat(result.get("f4").get().count()).isEqualTo(9L);
	}

	@Test
	public void shouldRemoveInconsistenciesFromArrayOfDecisionTables() {
		Dataset<Row>[] dts = DecisionTableFactory.extractDecisionTables(informationSystem);

		Dataset<Row>[] result = DecisionTableFactory.removeInconsistenciesMCD(dts);

		assertThat(result.length).isEqualTo(4);

		assertThat(result[0].count()).isEqualTo(6L);
		assertThat(result[1].count()).isEqualTo(7L);
		assertThat(result[2].count()).isEqualTo(9L);
		assertThat(result[3].count()).isEqualTo(9L);
	}
}
