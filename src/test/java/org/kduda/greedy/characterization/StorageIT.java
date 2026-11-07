package org.kduda.greedy.characterization;

import com.mongodb.gridfs.GridFSDBFile;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Test;
import org.kduda.greedy.exception.StorageException;
import org.kduda.greedy.integration.SpringIntegrationTest;
import org.kduda.greedy.model.FileModel;
import org.kduda.greedy.repository.data.FileRepository;
import org.kduda.greedy.repository.rules.RulesRepository;
import org.kduda.greedy.service.storage.MongoGridFsStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Pins how the legacy repositories use GridFS on a real MongoDB 4.4: content type, metadata, listing and deletion.
 * Names are unique per run because the "greedy" database is shared with other tests and the app.
 */
@SpringBootTest
public class StorageIT extends SpringIntegrationTest {

	@Autowired private FileRepository fileRepository;
	@Autowired private RulesRepository rulesRepository;
	@Autowired private MongoGridFsStorageService storage;

	private final String unique = UUID.randomUUID().toString();
	private final List<String> created = new ArrayList<>();

	@After
	public void tearDown() {
		created.forEach(storage::deleteById);
		rulesRepository.deleteByParent(unique);
	}

	@Test
	public void dataUploadIsStoredAsTextCsvWithTypeData() {
		String name = "storage-it-" + unique + ".csv";
		fileRepository.store(new MockMultipartFile("file", name, "text/plain", "a,d\n1,y".getBytes()));

		String id = idOf(fileRepository.listAll(), name);
		created.add(id);
		GridFSDBFile stored = storage.findFileById(id).get();
		assertThat(stored.getContentType()).isEqualTo("text/csv");
		assertThat(stored.getMetaData().get("type")).isEqualTo("data");
	}

	@Test
	public void emptyUploadIsRejected() {
		String name = "storage-it-" + unique + ".csv";

		Throwable thrown = catchThrowable(() -> fileRepository.store(new MockMultipartFile("file", name, "text/plain", new byte[0])));

		assertThat(thrown).isInstanceOf(StorageException.class).hasMessage("Failed to store empty file " + name);
	}

	@Test
	public void rulesAreListedAndDeletedByParent() {
		rulesRepository.store(new ByteArrayInputStream("x".getBytes()), "r.rul", unique, "text/plain");

		assertThat(rulesRepository.listByParent(unique)).extracting("name").containsExactly("r.rul");

		rulesRepository.deleteByParent(unique);
		assertThat(rulesRepository.listByParent(unique)).isEmpty();
	}

	@Test
	public void missingFileLoadsAsNull() {
		assertThat(fileRepository.loadResourceById(new ObjectId().toString())).isNull();
	}

	@Test
	public void deleteByFilenameRemovesEveryFileWithThatNameWhateverItsType() {
		String name = "storage-it-" + unique + ".csv";
		String dataId = store(name, "data");
		String rulesId = store(name, "association-rules");

		storage.deleteByFilename(name);

		assertThat(storage.findFileById(dataId).isPresent()).isFalse();
		assertThat(storage.findFileById(rulesId).isPresent()).isFalse();
	}

	@Test
	public void deleteByTypeRemovesFilesWithThatTypeMetadata() {
		String type = "storage-it-type-" + unique;
		store("storage-it-" + unique + ".csv", type);
		assertThat(storage.findFilesByType(type)).hasSize(1);

		storage.deleteByType(type);

		assertThat(storage.findFilesByType(type)).isEmpty();
	}

	private String store(String name, String type) {
		Map<String, String> metadata = Collections.singletonMap("type", type);
		String id = storage.storeFile(new ByteArrayInputStream("a,d\n1,y".getBytes()), name, "text/csv", metadata).get().getId().toString();
		created.add(id);
		return id;
	}

	private static String idOf(List<FileModel> files, String name) {
		for (FileModel file : files) {
			if (file.getName().equals(name)) {
				return file.getId();
			}
		}
		throw new AssertionError("not listed: " + name);
	}
}
