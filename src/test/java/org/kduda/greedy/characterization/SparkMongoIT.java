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

	private static final int FOUR_MEBIBYTES = 4 * 1024 * 1024;

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

	/**
	 * Spark 2.1 splits a file into chunks of max(4 MiB, (size + 4 MiB) / default parallelism) bytes; with the app's
	 * parallelism of 4 that is 4 MiB for files up to 12 MiB. A file over 4 MiB therefore has several partitions,
	 * show() reads only the first, and the temporary copy is gone when the rest is needed (registry #16).
	 */
	@Test
	public void largeFileIsReadAfterItsTemporaryCopyIsDeleted() {
		String id = store("spark-mongo-it-" + UUID.randomUUID() + ".csv", csvOfSize(FOUR_MEBIBYTES + 1024));
		Dataset<Row> data = sparkMongo.readCsvById(id);

		Throwable thrown = catchThrowable(() -> data.count());

		assertThat(thrown).isInstanceOf(SparkException.class);
		assertThat(rootCause(thrown)).isInstanceOf(FileNotFoundException.class);
	}

	@Test
	public void fileOfAtMostFourMebibytesIsReadCompletely() {
		String csv = csvOfSize(FOUR_MEBIBYTES - 1024);
		String id = store("spark-mongo-it-" + UUID.randomUUID() + ".csv", csv);

		Dataset<Row> data = sparkMongo.readCsvById(id);

		assertThat(data.count()).isEqualTo(csv.split("\n").length - 1);
	}

	/** A three-column CSV of at most {@code bytes} bytes and more than {@code bytes - 44}, all ASCII. */
	private static String csvOfSize(int bytes) {
		StringBuilder csv = new StringBuilder("a,b,d\n");
		for (long i = 0; csv.length() + 44 <= bytes; i++) {
			csv.append(String.format("%020d,%020d,%s\n", i, i * 7, i % 2 == 0 ? "y" : "n"));
		}
		return csv.toString();
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
