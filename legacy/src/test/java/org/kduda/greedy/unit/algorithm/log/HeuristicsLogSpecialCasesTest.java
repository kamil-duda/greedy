package org.kduda.greedy.unit.algorithm.log;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.junit.Test;
import org.kduda.greedy.algorithm.log.HeuristicsLog;
import org.kduda.greedy.spark.reader.csv.SparkCsvReader;
import org.kduda.greedy.unit.SpringUnitTest;
import org.kduda.greedy.unit.algorithm.DecisionTableUtils;
import org.springframework.beans.factory.annotation.Autowired;
import scala.Tuple2;
import scala.collection.immutable.List;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class HeuristicsLogSpecialCasesTest extends SpringUnitTest {

	@Autowired private SparkCsvReader sparkCsvReader;

	@Test
	public void shouldNotGenerateFromDegeneratedDecisionTable() throws IOException {
		scala.collection.immutable.Map<String, Dataset<Row>> dtsMapped =
			DecisionTableUtils.readTestFileAsDecisionTable(sparkCsvReader, getClass(), "paper-sample.csv");

		scala.collection.immutable.Map<String, List<List<Tuple2<String, String>>>> result = HeuristicsLog.calculateDecisionRules(dtsMapped);

		assertThat(result).isNotNull();
		assertThat(result.size()).isEqualTo(1);
		assertThat(result.get("f3").get().size()).isEqualTo(0);
	}

	@Test
	public void shouldNotGenerateFromEmptyDecisionTable() throws IOException {
		scala.collection.immutable.Map<String, Dataset<Row>> dtsMapped =
			DecisionTableUtils.readTestFileAsDecisionTable(sparkCsvReader, getClass(), "empty.csv");

		scala.collection.immutable.Map<String, List<List<Tuple2<String, String>>>> result = HeuristicsLog.calculateDecisionRules(dtsMapped);

		assertThat(result).isNotNull();
		assertThat(result.size()).isEqualTo(1);
		assertThat(result.get("f3").get().size()).isEqualTo(0);
	}

	@Test
	public void shouldCalculateLogBase2Of1() {
		double expected = 0;

		double actual = HeuristicsLog.log2(1);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void shouldCalculateLogBase2Of2() {
		double expected = 1;

		double actual = HeuristicsLog.log2(2);
		double actual2 = HeuristicsLog.log2(0 + 2F);

		assertThat(actual).isEqualTo(expected);
		assertThat(actual2).isEqualTo(expected);
	}

	@Test
	public void shouldCalculateLogBase2Of4() {
		double expected = 2;

		double actual = HeuristicsLog.log2(4);

		assertThat(actual).isEqualTo(expected);
	}
}
