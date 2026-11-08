package org.kduda.greedy.repository;

import com.mongodb.gridfs.GridFSDBFile;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.util.Optional;

public class RepositoryUtils {

	public static Pair<String, Resource> loadAsResource(Optional<GridFSDBFile> oFile) {
		Pair<String, Resource> result = null;

		if (oFile.isPresent()) {
			String filename = oFile.get().getFilename();
			Resource resource = new InputStreamResource(oFile.get().getInputStream());

			result = Pair.of(filename, resource);
		}
		return result;
	}
}
