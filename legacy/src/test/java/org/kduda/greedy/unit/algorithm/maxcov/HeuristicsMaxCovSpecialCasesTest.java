package org.kduda.greedy.unit.algorithm.maxcov;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.junit.Test;
import org.kduda.greedy.algorithm.maxcov.HeuristicsMaxCov;
import org.kduda.greedy.spark.reader.csv.SparkCsvReader;
import org.kduda.greedy.unit.SpringUnitTest;
import org.kduda.greedy.unit.algorithm.DecisionTableUtils;
import org.springframework.beans.factory.annotation.Autowired;
import scala.Tuple2;
import scala.collection.immutable.List;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class HeuristicsMaxCovSpecialCasesTest extends SpringUnitTest {

	@Autowired private SparkCsvReader sparkCsvReader;

	@Test
	public void shouldNotGenerateFromDegeneratedDecisionTable() throws IOException {
		scala.collection.immutable.Map<String, Dataset<Row>> dtsMapped =
			DecisionTableUtils.readTestFileAsDecisionTable(sparkCsvReader, getClass(), "paper-sample.csv");

		scala.collection.immutable.Map<String, List<List<Tuple2<String, String>>>> result = HeuristicsMaxCov.calculateDecisionRules(dtsMapped);

		assertThat(result).isNotNull();
		assertThat(result.size()).isEqualTo(1);
		assertThat(result.get("f3").get().size()).isEqualTo(0);
	}

	@Test
	public void shouldNotGenerateFromEmptyDecisionTable() throws IOException {
		scala.collection.immutable.Map<String, Dataset<Row>> dtsMapped =
			DecisionTableUtils.readTestFileAsDecisionTable(sparkCsvReader, getClass(), "empty.csv");

		scala.collection.immutable.Map<String, List<List<Tuple2<String, String>>>> result = HeuristicsMaxCov.calculateDecisionRules(dtsMapped);

		assertThat(result).isNotNull();
		assertThat(result.size()).isEqualTo(1);
		assertThat(result.get("f3").get().size()).isEqualTo(0);
	}
}
