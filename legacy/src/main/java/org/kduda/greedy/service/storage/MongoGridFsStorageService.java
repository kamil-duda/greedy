package org.kduda.greedy.service.storage;

import com.mongodb.MongoException;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSFile;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.gridfs.GridFsCriteria.whereFilename;
import static org.springframework.data.mongodb.gridfs.GridFsCriteria.whereMetaData;

@Slf4j
@Service
public class MongoGridFsStorageService implements FileStorageService {

	private final GridFsOperations operations;

	public MongoGridFsStorageService(GridFsOperations gridFsOperations) {
		this.operations = gridFsOperations;
	}

	@Override
	public Optional<GridFSFile> storeFile(@Nonnull InputStream inputStream, @Nonnull String filename,
										  String contentType, Map<String, String> metadata) {
		Optional<GridFSFile> result;

		result = Optional.of(operations.store(inputStream, filename, contentType, metadata));

		try {
			result.ifPresent(GridFSFile::validate);
		} catch (MongoException e) {
			log.error("File validation fail for file:" + filename, e);
		}

		return result;
	}

	@Override
	public Optional<GridFSDBFile> findFileByName(@NonNull String name) {
		return Optional.ofNullable(
			operations.findOne(
				query(
					whereFilename().is(name)
				)));
	}

	@Override
	public Optional<GridFSDBFile> findFileById(@NonNull String id) {
		return Optional.ofNullable(
			operations.findOne(
				query(
					where("_id").is(id)
				)));
	}

	@Override
	public List<GridFSDBFile> findFilesByType(@NonNull String type) {
		return operations.find(
			query(
				whereMetaData("type").is(type)
			));
	}

	@Override
	public List<GridFSDBFile> findFilesByParent(String parentId) {
		return operations.find(
			query(
				whereMetaData("parent").is(parentId)
			));
	}

	@Override
	public void deleteById(@NonNull String id) {
		operations.delete(
			query(
				where("_id").is(id)
			));
	}

	@Override
	public void deleteByFilename(@NonNull String filename) {
		operations.delete(
			query(
				whereFilename().is(filename)
			));
	}

	@Override
	public void deleteByType(@NonNull String type) {
		operations.delete(
			query(
				whereMetaData("type").is(type)
			));
	}

	@Override
	public void deleteByParent(String parentId) {
		operations.delete(
			query(
				whereMetaData("parent").is(parentId)
			));
	}
}