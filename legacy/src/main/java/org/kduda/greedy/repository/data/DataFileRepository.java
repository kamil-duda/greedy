package org.kduda.greedy.repository.data;

import com.mongodb.gridfs.GridFSDBFile;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;
import org.kduda.greedy.domain.FileContentTypes;
import org.kduda.greedy.domain.GreedyTableTypes;
import org.kduda.greedy.exception.StorageException;
import org.kduda.greedy.model.FileModel;
import org.kduda.greedy.repository.RepositoryUtils;
import org.kduda.greedy.service.storage.MongoGridFsStorageService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class DataFileRepository implements FileRepository {

	private final MongoGridFsStorageService storageService;

	public DataFileRepository(MongoGridFsStorageService storageService) {
		this.storageService = storageService;
	}

	@Override
	public void store(MultipartFile file) {
		try {
			if (file.isEmpty()) {
				throw new StorageException("Failed to store empty file " + file.getOriginalFilename());
			}
			Map<String, String> metadata = new HashMap<>();
			metadata.put("type", GreedyTableTypes.DATA.getType());

			storageService.storeFile(file.getInputStream(), file.getOriginalFilename(), FileContentTypes.CSV.getType(), metadata);

		} catch (IOException e) {
			throw new StorageException("Failed to store file " + file.getOriginalFilename(), e);
		}
	}

	@Override
	public List<FileModel> listAll() {
		return storageService.findFilesByType(GreedyTableTypes.DATA.getType())
							 .stream()
							 .map(file -> new FileModel(file.getFilename(), file.getId().toString()))
							 .collect(Collectors.toList());
	}

	@Override
	public FileModel listById(String id) {
		return storageService.findFileById(id)
							 .map(file -> new FileModel(file.getFilename(), file.getId().toString()))
							 .get();
	}

	@Override
	public Pair<String, Resource> loadResourceById(@NonNull String id) {
		Optional<GridFSDBFile> oFile = storageService.findFileById(id);
		return RepositoryUtils.loadAsResource(oFile);
	}

	@Override
	public Pair<String, Resource> loadResourceByFilename(@NonNull String filename) {
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
	public void deleteAll() {
		storageService.deleteByType(GreedyTableTypes.DATA.getType());
	}

	@Override
	public void init() {
	}
}
