package org.kduda.greedy.repository.rules;

import com.mongodb.gridfs.GridFSDBFile;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;
import org.kduda.greedy.domain.GreedyTableTypes;
import org.kduda.greedy.model.FileModel;
import org.kduda.greedy.repository.RepositoryUtils;
import org.kduda.greedy.service.storage.MongoGridFsStorageService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class RulesFileRepository implements RulesRepository {

	private final MongoGridFsStorageService storageService;

	public RulesFileRepository(MongoGridFsStorageService storageService) {
		this.storageService = storageService;
	}

	@Override
	public void store(@Nonnull InputStream fileInputStream, @Nonnull String fileName, @Nonnull String parentId, @Nonnull String contentType) {

		Map<String, String> metadata = new HashMap<>();
		metadata.put("type", GreedyTableTypes.RULES.getType());
		metadata.put("parent", parentId);

		storageService.storeFile(fileInputStream, fileName, contentType, metadata);
	}

	@Override
	public List<FileModel> listAll() {
		return storageService.findFilesByType(GreedyTableTypes.RULES.getType())
							 .stream()
							 .map(file -> new FileModel(file.getFilename(), file.getId().toString()))
							 .collect(Collectors.toList());
	}

	@Override
	public List<FileModel> listByParent(String parentId) {
		return storageService.findFilesByParent(parentId)
							 .stream()
							 .map(file -> new FileModel(file.getFilename(), file.getId().toString()))
							 .collect(Collectors.toList());
	}

	@Override
	public Pair<String, Resource> loadResourceById(String id) {
		Optional<GridFSDBFile> oFile = storageService.findFileById(id);
		return RepositoryUtils.loadAsResource(oFile);
	}

	@Override
	public Pair<String, Resource> loadResourceByFilename(String filename) {
		Optional<GridFSDBFile> oFile = storageService.findFileByName(filename);
		return RepositoryUtils.loadAsResource(oFile);
	}

	@Override
	public void deleteById(@NonNull String id) {
		storageService.deleteById(id);
	}

	@Override
	public void deleteByFilename(@NonNull String name) {
		storageService.deleteByFilename(name);
	}

	@Override
	public void deleteByParent(String parentId) {
		storageService.deleteByParent(parentId);
	}

	@Override
	public void deleteAll() {
		storageService.deleteByType(GreedyTableTypes.RULES.getType());
	}

	@Override
	public void init() {
	}
}
