package org.kduda.greedy.unit.algorithm.util;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kduda.greedy.algorithm.util.ArrayBufferHelperFactory;
import org.kduda.greedy.algorithm.util.GreedyUtils;
import org.kduda.greedy.spark.reader.csv.SparkCsvReader;
import org.kduda.greedy.unit.SpringUnitTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import scala.Tuple2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class GreedyUtilsTest extends SpringUnitTest {

	@Autowired private SparkCsvReader sparkCsvReader;

	private Row[] data = new Row[]{};

	public static <T> scala.collection.immutable.List<T> scalaList(List<T> javaList) {
		return scala.collection.JavaConversions.collectionAsScalaIterable(javaList).toList();
	}

	@Before
	public void setUp() throws IOException {
		ClassPathResource resource = new ClassPathResource("/files/sample.csv", getClass());
		File file = new File(resource.getURL().getPath());

		Map<String, String> options = new HashMap<>();
		options.put("header", "true");

		Dataset<Row> is = sparkCsvReader.read(file, options);
		data = is.collectAsList().toArray(data);
	}

	@After
	public void tearDown() {
		data = null;
	}

	@Test
	public void shouldFilterByColumn() {
		List<Tuple2<String, String>> columns = new ArrayList<>();
		columns.add(new Tuple2<>("f2", "1"));
		columns.add(new Tuple2<>("f3", "0"));

		Row[] result = GreedyUtils.filterByColumns(data, scalaList(columns));

		assertThat(result.length).isEqualTo(1);
	}

	@Test
	public void shouldCountDistinctGroups() {
		int result = GreedyUtils.countDistinctValuesIn(data, "f4");

		assertThat(result).isEqualTo(2);
	}

	@Test
	public void shouldCalculateSupportEqualToOne() {
		@SuppressWarnings("unchecked")
		Tuple2<String, String>[] decisionRule = new Tuple2[]{new Tuple2<>("f3", "1")};

		scala.collection.immutable.List<Tuple2<String, String>> result =
			GreedyUtils.calculateSupport(data, ArrayBufferHelperFactory.of(decisionRule));

		assertThat(result.head()._2).isEqualTo("1");
	}

	@Test
	public void shouldCalculateSupportGreaterThanOne() {
		@SuppressWarnings("unchecked")
		Tuple2<String, String>[] decisionRule = new Tuple2[]{new Tuple2<>("f3", "0"), new Tuple2<>("f2", "0")};

		scala.collection.immutable.List<Tuple2<String, String>> result =
			GreedyUtils.calculateSupport(data, ArrayBufferHelperFactory.of(decisionRule));

		assertThat(result.head()._2).isEqualTo("2");
	}
}
