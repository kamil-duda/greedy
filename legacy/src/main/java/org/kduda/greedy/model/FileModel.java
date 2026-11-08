package org.kduda.greedy.model;

import lombok.Value;

/**
 * This class is used to represent file's (name, id) pair for front-end representation - mainly in some lists.
 */
@Value
public class FileModel {
	String name;
	String id;
}
