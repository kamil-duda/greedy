package org.kduda.greedy.unit.spark;

import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kduda.greedy.spark.reader.csv.SparkCsvReader;
import org.kduda.greedy.unit.SpringUnitTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class CsvInputTest extends SpringUnitTest {

	@Autowired private SparkCsvReader sparkCsvReader = null;

	private Map<String, String> options = null;
	private File file = null;

	@Before
	public void setUp() throws IOException {
		options = new HashMap<>();

		ClassPathResource resource = new ClassPathResource("/files/paper-sample.csv", getClass());
		file = new File(resource.getURL().getPath());
	}

	@After
	public void tearDown() {
		file = null;
	}

	@Test
	public void shouldReadCsvFile() {
		Dataset<Row> csv = sparkCsvReader.read(file, null);

		assertThat(csv).isNotNull();
		assertThat(csv.count()).isEqualTo(4);
	}

	@Test
	public void shouldReadCsvFileWithOptions() {
		options.put("headers", "true");
		Dataset<Row> csv = sparkCsvReader.read(file, options);

		assertThat(csv).isNotNull();
		assertThat(csv.count()).isEqualTo(4);
	}

	@Test
	public void shouldReadCsvFileWithEmptyOptions() {
		Dataset<Row> csv = sparkCsvReader.read(file, options);

		assertThat(csv).isNotNull();
		assertThat(csv.count()).isEqualTo(4);
	}

}
