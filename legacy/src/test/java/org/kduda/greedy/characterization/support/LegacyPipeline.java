package org.kduda.greedy.characterization.support;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.kduda.greedy.algorithm.DecisionTableFactory;
import org.kduda.greedy.algorithm.log.HeuristicsLog;
import org.kduda.greedy.algorithm.m.HeuristicsM;
import org.kduda.greedy.algorithm.maxcov.HeuristicsMaxCov;
import org.kduda.greedy.algorithm.parser.ToStringParser;
import org.kduda.greedy.algorithm.poly.HeuristicsPoly;
import org.kduda.greedy.algorithm.rm.HeuristicsRM;
import org.kduda.greedy.spark.reader.csv.CsvFileSparkCsvReader;
import scala.Tuple2;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * The steps of DataExplorationService.exploreAndSave without MongoDB: read CSV, prepare decision tables, apply MCD,
 * run a heuristic, format. LegacyPipelineMatchesServiceTest proves it produces what the service stores.
 */
public final class LegacyPipeline {

	public enum Mode {
		DT("dt"), IS("is");

		public final String formValue;

		Mode(String formValue) {
			this.formValue = formValue;
		}
	}

	public enum Heuristic {
		M("m"), RM("rm"), MAX_COV("maxCov"), POLY("poly"), LOG("log");

		public final String formValue;

		Heuristic(String formValue) {
			this.formValue = formValue;
		}
	}

	public enum Format {
		CSV("csv", ".csv"), RSES("rses", ".rul"), CSVM("csvm", "-wmetadata.csv");

		public final String formValue;
		public final String suffix;

		Format(String formValue, String suffix) {
			this.formValue = formValue;
			this.suffix = suffix;
		}
	}

	private LegacyPipeline() {
	}

	/** Reads a CSV with a header row, as SparkMongo does after copying the file out of GridFS. */
	public static Dataset<Row> readCsv(File file) {
		Map<String, String> options = new HashMap<>();
		options.put("header", "true");
		return new CsvFileSparkCsvReader().read(file, options);
	}

	@SuppressWarnings("unchecked")
	public static scala.collection.immutable.Map<String, scala.collection.immutable.List<scala.collection.immutable.List<Tuple2<String, String>>>> induce(
		Dataset<Row> csv, Mode mode, Heuristic heuristic) {
		Dataset<Row>[] tables = mode == Mode.IS ? DecisionTableFactory.extractDecisionTables(csv) : new Dataset[]{csv};
		scala.collection.immutable.Map<String, Dataset<Row>> decisionTables =
			DecisionTableFactory.createMapOf(DecisionTableFactory.removeInconsistenciesMCD(tables));
		switch (heuristic) {
			case M:
				return HeuristicsM.calculateDecisionRules(decisionTables);
			case RM:
				return HeuristicsRM.calculateDecisionRules(decisionTables);
			case MAX_COV:
				return HeuristicsMaxCov.calculateDecisionRules(decisionTables);
			case POLY:
				return HeuristicsPoly.calculateDecisionRules(decisionTables);
			case LOG:
				return HeuristicsLog.calculateDecisionRules(decisionTables);
			default:
				throw new IllegalArgumentException("unknown heuristic " + heuristic);
		}
	}

	public static String format(
		scala.collection.immutable.Map<String, scala.collection.immutable.List<scala.collection.immutable.List<Tuple2<String, String>>>> rules,
		Format format) {
		switch (format) {
			case CSV:
				return ToStringParser.buildStringCSV(rules);
			case RSES:
				return ToStringParser.buildStringRSES(rules);
			case CSVM:
				return ToStringParser.buildStringCSVWithMeta(rules);
			default:
				throw new IllegalArgumentException("unknown format " + format);
		}
	}
}
