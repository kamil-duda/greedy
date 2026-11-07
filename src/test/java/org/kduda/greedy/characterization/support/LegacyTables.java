package org.kduda.greedy.characterization.support;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.kduda.greedy.algorithm.DecisionTableFactory;
import org.kduda.greedy.spark.GreedySparkInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds small in-memory tables and prepares them exactly as DataExplorationService does, without Spring or MongoDB.
 */
public final class LegacyTables {

	private LegacyTables() {
	}

	/**
	 * A table of nullable string columns. Fields are comma-separated; an empty field becomes null, as Spark 2.1's CSV
	 * reader reads it. Values containing commas cannot be expressed here.
	 */
	public static Dataset<Row> table(String header, String... rows) {
		List<StructField> fields = new ArrayList<>();
		for (String column : header.split(",", -1)) {
			fields.add(DataTypes.createStructField(column, DataTypes.StringType, true));
		}
		List<Row> data = new ArrayList<>();
		for (String row : rows) {
			String[] values = row.split(",", -1);
			Object[] cells = new Object[values.length];
			for (int i = 0; i < values.length; i++) {
				cells[i] = values[i].isEmpty() ? null : values[i];
			}
			data.add(RowFactory.create(cells));
		}
		return GreedySparkInstance.sparkSession().createDataFrame(data, DataTypes.createStructType(fields));
	}

	/** The service's "dt" path: the table as one decision table (last column = decision), MCD applied, keyed by decision. */
	@SuppressWarnings("unchecked")
	public static scala.collection.immutable.Map<String, Dataset<Row>> asDecisionTable(Dataset<Row> table) {
		return DecisionTableFactory.createMapOf(DecisionTableFactory.removeInconsistenciesMCD(new Dataset[]{table}));
	}

	/** The service's "is" path: one decision table per column (that column last), MCD applied, keyed by decision. */
	public static scala.collection.immutable.Map<String, Dataset<Row>> asInformationSystem(Dataset<Row> table) {
		return DecisionTableFactory.createMapOf(
			DecisionTableFactory.removeInconsistenciesMCD(DecisionTableFactory.extractDecisionTables(table)));
	}

	/** Every row as Row.toString(), e.g. "[0,n]" or "[null,y]", sorted. */
	public static List<String> rows(Dataset<Row> table) {
		List<String> rows = new ArrayList<>();
		for (Row row : table.collectAsList()) {
			rows.add(row.toString());
		}
		Collections.sort(rows);
		return rows;
	}
}
