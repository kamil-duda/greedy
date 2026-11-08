package org.kduda.greedy.repository.rules;

import org.apache.commons.lang3.tuple.Pair;
import org.kduda.greedy.model.FileModel;
import org.springframework.core.io.Resource;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.util.List;

public interface RulesRepository {

	void init();

	void store(@Nonnull InputStream fileInputStream, @Nonnull String fileName, @Nonnull String parentId, @Nonnull String contentType);

	List<FileModel> listAll();

	List<FileModel> listByParent(String parentId);

	Pair<String, Resource> loadResourceById(String id);

	Pair<String, Resource> loadResourceByFilename(String filename);

	void deleteById(String id);

	void deleteByFilename(String name);

	void deleteByParent(String parentId);

	void deleteAll();
}
