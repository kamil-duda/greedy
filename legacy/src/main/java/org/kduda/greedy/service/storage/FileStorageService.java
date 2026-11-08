package org.kduda.greedy.service.storage;

import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSFile;

import javax.annotation.Nonnull;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface FileStorageService {

	Optional<GridFSFile> storeFile(@Nonnull InputStream inputStream, @Nonnull String filename, String contentType,
								   Map<String, String> metadata);

	Optional<GridFSDBFile> findFileByName(String name);

	Optional<GridFSDBFile> findFileById(String id);

	List<? extends GridFSFile> findFilesByType(String type);

	List<? extends GridFSFile> findFilesByParent(String parentId);

	void deleteById(String id);

	void deleteByFilename(String filename);

	void deleteByType(String type);

	void deleteByParent(String parentId);
}
