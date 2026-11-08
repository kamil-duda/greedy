package org.kduda.greedy.repository.data;

import org.apache.commons.lang3.tuple.Pair;
import org.kduda.greedy.model.FileModel;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileRepository {

	void init();

	void store(MultipartFile file);

	List<FileModel> listAll();

	FileModel listById(String id);

	Pair<String, Resource> loadResourceById(String id);

	Pair<String, Resource> loadResourceByFilename(String filename);

	void deleteById(String id);

	void deleteByFilename(String name);

	void deleteAll();

}
