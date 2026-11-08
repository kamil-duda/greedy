package org.kduda.greedy.characterization;

import org.junit.Test;
import scala.collection.JavaConverters;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kduda.greedy.characterization.support.LegacyTables.asDecisionTable;
import static org.kduda.greedy.characterization.support.LegacyTables.asInformationSystem;
import static org.kduda.greedy.characterization.support.LegacyTables.rows;
import static org.kduda.greedy.characterization.support.LegacyTables.table;

/**
 * Pins how the legacy code turns a CSV into decision tables: duplicate removal and most-common-decision (MCD)
 * inconsistency removal in DecisionTableFactory, as DataExplorationService uses them.
 */
public class DecisionTablePreparationTest {

	@Test
	public void mostCommonDecisionIsCountedAfterRemovingDuplicateRows() {
		// raw rows: n=4, y=3; distinct rows: y=3, n=2 -> the inconsistent group A=0 gets y
		List<String> rows = rows(asDecisionTable(table("A,D", "0,y", "0,n", "1,n", "1,n", "1,n", "2,y", "3,y")).get("D").get());

		assertThat(rows).containsExactly("[0,y]", "[1,n]", "[2,y]", "[3,y]");
	}

	@Test
	public void inconsistentGroupTakesTheGloballyMostFrequentDecision() {
		List<String> rows = rows(asDecisionTable(table("A,D", "0,y", "0,n", "1,n", "2,n", "3,y")).get("D").get());

		assertThat(rows).containsExactly("[0,n]", "[1,n]", "[2,n]", "[3,y]");
	}

	@Test
	public void equalDecisionFrequenciesPickAnUnspecifiedDecision() {
		// nondeterministic in legacy: ties in the frequency ranking have no defined order (registry #2)
		List<String> rows = rows(asDecisionTable(table("A,D", "0,y", "0,n", "1,y", "2,n")).get("D").get());

		assertThat(rows).hasSize(3).contains("[1,y]", "[2,n]");
		assertThat(rows.contains("[0,y]") ^ rows.contains("[0,n]")).isTrue();
	}

	@Test
	public void inconsistentGroupWithEmptyConditionIsLeftUntouched() {
		// the MCD join on condition columns never matches null, so both rows stay (registry #10)
		List<String> rows = rows(asDecisionTable(table("A,D", ",y", ",n", "1,n")).get("D").get());

		assertThat(rows).containsExactly("[1,n]", "[null,n]", "[null,y]");
	}

	@Test
	public void informationSystemModeBuildsOneTablePerColumnWithThatColumnLast() {
		scala.collection.immutable.Map<String, org.apache.spark.sql.Dataset<org.apache.spark.sql.Row>> tables =
			asInformationSystem(table("A,B,C", "0,1,2", "1,1,2"));

		assertThat(JavaConverters.asJavaIterableConverter(tables.keys()).asJava()).containsOnly("A", "B", "C");
		assertThat(tables.get("A").get().columns()).containsExactly("B", "C", "A");
	}
}
