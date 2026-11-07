package org.kduda.greedy.characterization;

import com.mongodb.gridfs.GridFSFile;
import org.apache.spark.SparkException;
import org.apache.spark.sql.AnalysisException;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.junit.After;
import org.junit.Test;
import org.kduda.greedy.domain.FileContentTypes;
import org.kduda.greedy.integration.SpringIntegrationTest;
import org.kduda.greedy.service.storage.FileStorageService;
import org.kduda.greedy.spark.reader.mongo.SparkMongoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Pins how SparkMongo reads a GridFS file: it copies it to tmp-storage/&lt;upload name&gt;, reads it with Spark,
 * caches it, shows it and deletes the copy.
 */
@SpringBootTest
public class SparkMongoIT extends SpringIntegrationTest {

	@Autowired private FileStorageService storage;
	@Autowired private SparkMongoService sparkMongo;

	private final List<String> created = new ArrayList<>();

	@After
	public void tearDown() {
		created.forEach(storage::deleteById);
	}

	@Test
	public void uploadNameIsUsedAsTemporaryFilePath() {
		// "sub/" does not exist under tmp-storage, so the copy fails silently and Spark finds nothing to read
		String id = store("sub/" + UUID.randomUUID() + ".csv", "a,d\n1,y\n");

		Throwable thrown = catchThrowable(() -> sparkMongo.readCsvById(id));

		assertThat(thrown).isInstanceOf(AnalysisException.class).hasMessageContaining("Path does not exist");
	}

	@Test
	public void largeFileIsReadAfterItsTemporaryCopyIsDeleted() {
		// ~20 MB: Spark splits it into several partitions, show() reads only the first, then the copy is deleted
		StringBuilder csv = new StringBuilder("a,b,d\n");
		for (int i = 0; i < 450000; i++) {
			csv.append(String.format("%020d,%020d,%s%n", i, i * 7L, i % 2 == 0 ? "y" : "n"));
		}
		String id = store("spark-mongo-it-" + UUID.randomUUID() + ".csv", csv.toString());
		Dataset<Row> data = sparkMongo.readCsvById(id);

		Throwable thrown = catchThrowable(() -> data.count());

		assertThat(thrown).isInstanceOf(SparkException.class);
		assertThat(rootCause(thrown)).isInstanceOf(FileNotFoundException.class);
	}

	private String store(String name, String content) {
		GridFSFile file = storage.storeFile(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), name,
			FileContentTypes.CSV.getType(), new HashMap<>()).get();
		String id = file.getId().toString();
		created.add(id);
		return id;
	}

	private static Throwable rootCause(Throwable thrown) {
		Throwable cause = thrown;
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
		}
		return cause;
	}
}
