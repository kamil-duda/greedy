package org.kduda.greedy.unit.algorithm;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.kduda.greedy.algorithm.DecisionTableFactory;
import org.kduda.greedy.spark.reader.csv.SparkCsvReader;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DecisionTableUtils {
	public static scala.collection.immutable.Map<String, Dataset<Row>> readTestFileAsDecisionTable(SparkCsvReader sparkCsvReader,
																								   Class clazz, String filename) throws IOException {
		ClassPathResource resource = new ClassPathResource("/files/" + filename, clazz);
		File file = new File(resource.getURL().getPath());

		Map<String, String> options = new HashMap<>();
		options.put("header", "true");

		Dataset<Row> dts = sparkCsvReader.read(file, options);
		//noinspection unchecked
		return DecisionTableFactory.createMapOf(new Dataset[]{dts});
	}
}
