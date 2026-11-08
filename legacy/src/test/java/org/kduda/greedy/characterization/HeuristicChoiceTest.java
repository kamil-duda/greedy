package org.kduda.greedy.characterization;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.junit.Test;
import org.kduda.greedy.algorithm.log.HeuristicsLog;
import org.kduda.greedy.algorithm.m.HeuristicsM;
import org.kduda.greedy.algorithm.maxcov.HeuristicsMaxCov;
import org.kduda.greedy.algorithm.poly.HeuristicsPoly;
import org.kduda.greedy.algorithm.rm.HeuristicsRM;
import scala.Tuple2;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.kduda.greedy.characterization.support.LegacyRules.render;
import static org.kduda.greedy.characterization.support.LegacyTables.asDecisionTable;
import static org.kduda.greedy.characterization.support.LegacyTables.table;

/**
 * Pins which attribute each legacy heuristic adds to a rule, how ties are broken, how identical rules merge and
 * what support they get. Expected lists were verified against the legacy code on 2026-10-27; they differ between
 * heuristics and column orders, which is what makes them discriminate.
 */
public class HeuristicChoiceTest {

	private interface Heuristic extends Function<scala.collection.immutable.Map<String, Dataset<Row>>,
		scala.collection.immutable.Map<String, scala.collection.immutable.List<scala.collection.immutable.List<Tuple2<String, String>>>>> {
	}

	private static final Heuristic M = HeuristicsM::calculateDecisionRules;
	private static final Heuristic RM = HeuristicsRM::calculateDecisionRules;
	private static final Heuristic MAX_COV = HeuristicsMaxCov::calculateDecisionRules;
	private static final Heuristic POLY = HeuristicsPoly::calculateDecisionRules;
	private static final Heuristic LOG = HeuristicsLog::calculateDecisionRules;
	private static final List<Heuristic> ALL = Arrays.asList(M, RM, MAX_COV, POLY, LOG);

	// T1: for row (0,0,y), M(T(A))=2 vs M(T(B))=1, but RM(T(A))=2/5 < RM(T(B))=1/2
	private static Dataset<Row> t1() {
		return table("A,B,D", "0,0,y", "0,1,y", "0,2,y", "0,3,n", "0,4,n", "1,0,n");
	}

	// T1': the same data with the condition columns swapped
	private static Dataset<Row> t1Swapped() {
		return table("B,A,D", "0,0,y", "1,0,y", "2,0,y", "3,0,n", "4,0,n", "0,1,n");
	}

	private static List<String> rules(Heuristic heuristic, Dataset<Row> table) {
		return render(heuristic.apply(asDecisionTable(table)));
	}

	@Test
	public void mPrefersFewestOtherDecisionRowsWhileRmPrefersLowestRatio() {
		assertThat(rules(M, t1())).containsExactly(
			"A=1 => D=n [1]", "B=0 & A=0 => D=y [1]", "B=1 => D=y [1]", "B=2 => D=y [1]", "B=3 => D=n [1]", "B=4 => D=n [1]");
		assertThat(rules(RM, t1())).containsExactly(
			"A=0 & B=0 => D=y [1]", "A=1 => D=n [1]", "B=1 => D=y [1]", "B=2 => D=y [1]", "B=3 => D=n [1]", "B=4 => D=n [1]");
	}

	@Test
	public void maxCovAndPolyPreferTheSmallestLossOfSameDecisionRows() {
		String[] expected = {
			"A=0 & B=0 => D=y [1]", "A=0 & B=1 => D=y [1]", "A=0 & B=2 => D=y [1]", "A=1 => D=n [1]", "B=3 => D=n [1]", "B=4 => D=n [1]"};
		assertThat(rules(MAX_COV, t1())).containsExactly(expected);
		assertThat(rules(POLY, t1())).containsExactly(expected);
	}

	@Test
	public void logBreaksExactScoreTiesByColumnOrder() {
		// for row (0,0,y) both attributes score exactly 1.0: 1/log2(2) and 2/log2(4)
		assertThat(rules(LOG, t1())).containsExactly(
			"A=0 & B=0 => D=y [1]", "A=1 => D=n [1]", "B=1 => D=y [1]", "B=2 => D=y [1]", "B=3 => D=n [1]", "B=4 => D=n [1]");
		assertThat(rules(LOG, t1Swapped())).containsExactly(
			"A=1 => D=n [1]", "B=0 & A=0 => D=y [1]", "B=1 => D=y [1]", "B=2 => D=y [1]", "B=3 => D=n [1]", "B=4 => D=n [1]");
	}

	@Test
	public void polyIgnoresColumnOrderWhenScoresDiffer() {
		assertThat(rules(POLY, t1Swapped())).containsExactly(
			"A=0 & B=0 => D=y [1]", "A=1 => D=n [1]", "B=1 => D=y [1]", "B=2 => D=y [1]", "B=3 => D=n [1]", "B=4 => D=n [1]");
	}

	@Test
	public void maxCovRulesDependOnColumnOrder() {
		assertThat(rules(MAX_COV, t1Swapped())).containsExactly(
			"A=0 & B=0 => D=y [1]", "A=0 & B=1 => D=y [1]", "A=0 & B=2 => D=y [1]", "B=0 & A=1 => D=n [1]", "B=3 => D=n [1]", "B=4 => D=n [1]");
	}

	@Test
	public void tiesGoToTheEarliestColumnForEveryHeuristic() {
		for (Heuristic heuristic : ALL) {
			assertThat(rules(heuristic, table("A,B,D", "0,0,y", "0,1,n", "1,0,n", "1,1,y"))).containsExactly(
				"A=0 & B=0 => D=y [1]", "A=0 & B=1 => D=n [1]", "A=1 & B=0 => D=n [1]", "A=1 & B=1 => D=y [1]");
			assertThat(rules(heuristic, table("B,A,D", "0,0,y", "1,0,n", "0,1,n", "1,1,y"))).containsExactly(
				"B=0 & A=0 => D=y [1]", "B=0 & A=1 => D=n [1]", "B=1 & A=0 => D=n [1]", "B=1 & A=1 => D=y [1]");
		}
	}

	@Test
	public void identicalRulesFromDifferentRowsAreMergedAndSupportIsCounted() {
		for (Heuristic heuristic : ALL) {
			assertThat(rules(heuristic, table("A,B,D", "0,0,y", "0,1,y", "1,0,n", "1,1,n")))
				.containsExactly("A=0 => D=y [2]", "A=1 => D=n [2]");
		}
	}

	@Test
	public void attributesThatDoNotSeparateDecisionsAreNotChosen() {
		// C is constant: choosing it changes nothing; maxCov's beta > 0 filter is what skips it there
		for (Heuristic heuristic : ALL) {
			assertThat(rules(heuristic, table("C,A,D", "k,0,y", "k,1,n")))
				.containsExactly("A=0 => D=y [1]", "A=1 => D=n [1]");
		}
	}

	@Test
	public void emptyCellsInAConsistentTableAreOrdinaryValues() {
		// Scala's == is null-safe, so an empty condition or decision matches only other empty cells
		Dataset<Row> table = table("A,B,D", ",0,y", ",1,", "1,0,n");
		for (Heuristic heuristic : Arrays.asList(M, RM, POLY, LOG)) {
			assertThat(rules(heuristic, table))
				.containsExactly("A=1 => D=n [1]", "A=null & B=0 => D=y [1]", "B=1 => D=null [1]");
		}
		// maxCov: for (null,1,null) both columns have alpha 0, so the tie goes to A
		assertThat(rules(MAX_COV, table))
			.containsExactly("A=1 => D=n [1]", "A=null & B=0 => D=y [1]", "A=null & B=1 => D=null [1]");
	}

	@Test
	public void emptyConditionInInconsistentGroupCrashesEveryHeuristic() {
		// MCD leaves the null-condition pair inconsistent, the loop runs out of columns and takes head of nothing (registry #10)
		for (Heuristic heuristic : ALL) {
			Throwable thrown = catchThrowable(() -> rules(heuristic, table("A,D", ",y", ",n", "1,n")));

			assertThat(thrown).isInstanceOf(NoSuchElementException.class).hasMessage("next on empty iterator");
		}
	}
}
