package org.kduda.greedy.characterization;

import com.mongodb.MongoException;
import com.mongodb.gridfs.GridFSFile;
import org.junit.Test;
import org.kduda.greedy.service.storage.MongoGridFsStorageService;
import org.springframework.data.mongodb.gridfs.GridFsOperations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what MongoGridFsStorageService does when GridFS validation fails; needs no MongoDB, unlike {@link StorageIT}.
 */
public class StorageValidationTest {

	/** A file that fails GridFS validation (MD5 mismatch) is only logged; it stays stored and is returned. */
	@Test
	public void failedValidationIsOnlyLogged() {
		GridFsOperations operations = mock(GridFsOperations.class);
		GridFSFile file = mock(GridFSFile.class);
		doThrow(new MongoException("md5 mismatch")).when(file).validate();
		when(operations.store(any(InputStream.class), anyString(), anyString(), any(Object.class))).thenReturn(file);

		Optional<GridFSFile> stored = new MongoGridFsStorageService(operations)
			.storeFile(new ByteArrayInputStream("a".getBytes()), "f.csv", "text/csv", Collections.emptyMap());

		assertThat(stored.get()).isSameAs(file);
	}
}
