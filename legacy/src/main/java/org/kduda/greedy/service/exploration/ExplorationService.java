package org.kduda.greedy.service.exploration;

import org.kduda.greedy.model.ExploreRequestModel;

public interface ExplorationService {
	void exploreAndSave(String id, ExploreRequestModel exploreDetails);
}
