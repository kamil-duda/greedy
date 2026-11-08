package org.kduda.greedy.integration.spark;

import com.mongodb.gridfs.GridFSFile;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kduda.greedy.domain.FileContentTypes;
import org.kduda.greedy.integration.SpringIntegrationTest;
import org.kduda.greedy.service.storage.FileStorageService;
import org.kduda.greedy.spark.reader.mongo.SparkMongoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class MongoSparkIT extends SpringIntegrationTest {

	private final static String FILE_NAME = "sample.csv";
	private final static int EXPECTED_COUNT = 4;

	@Autowired private FileStorageService storageService;
	@Autowired private SparkMongoService sparkMongo;

	private GridFSFile gridFSFile;

	@Before
	public void setUp() throws IOException {
		Map<String, String> metadata = new HashMap<>();
		metadata.put("scope", "test");

		Resource resource = new ClassPathResource("/files/" + FILE_NAME);

		Optional<GridFSFile> oGridFSFile = storageService.storeFile(resource.getInputStream(), FILE_NAME, FileContentTypes.CSV
			.getType(), metadata);
		oGridFSFile.ifPresent(file -> gridFSFile = file);
	}

	@After
	public void tearDown() {
		storageService.deleteById(gridFSFile.getId().toString());
	}

	@Test
	public void shouldReadCsvFromMongoByNameAsDataset() {
		Dataset<Row> csv = sparkMongo.readCsvByName(FILE_NAME);

		assertThat(csv).isNotNull();
		assertThat(csv.count()).isEqualTo(EXPECTED_COUNT);
	}

	@Test
	public void shouldReadCsvFromMongoByIdAsDataset() {
		Dataset<Row> csv = sparkMongo.readCsvById(gridFSFile.getId().toString());

		assertThat(csv).isNotNull();
		assertThat(csv.count()).isEqualTo(EXPECTED_COUNT);
	}
}
