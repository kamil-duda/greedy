package org.kduda.greedy.characterization;

import org.junit.Test;
import org.kduda.greedy.algorithm.m.HeuristicsM;
import org.kduda.greedy.algorithm.parser.ToStringParser;
import scala.Tuple2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.kduda.greedy.characterization.support.LegacyRules.ruleSet;
import static org.kduda.greedy.characterization.support.LegacyTables.asDecisionTable;
import static org.kduda.greedy.characterization.support.LegacyTables.table;

/**
 * Pins the exact text of the three legacy output formats (ToStringParser).
 */
public class OutputFormatTest {

	private static scala.collection.immutable.Map<String, scala.collection.immutable.List<scala.collection.immutable.List<Tuple2<String, String>>>> twoRules() {
		return ruleSet("B=0 & A=0 => D=y [1]", "A=1 => D=n [1]");
	}

	private static scala.collection.immutable.Map<String, scala.collection.immutable.List<scala.collection.immutable.List<Tuple2<String, String>>>> noRules() {
		// a table with a single decision value yields an empty rule list
		return HeuristicsM.calculateDecisionRules(asDecisionTable(table("A,D", "0,y", "1,y")));
	}

	@Test
	public void rsesWritesConditionsInStoredOrderAndSupportTwice() {
		assertThat(ToStringParser.buildStringRSES(twoRules())).isEqualTo("(B=0)&(A=0)=>(D=y[1]) 1\n(A=1)=>(D=n[1]) 1\n");
	}

	@Test
	public void csvConcatenatesConditionsWithoutSeparatorAndOmitsDecisionName() {
		assertThat(ToStringParser.buildStringCSV(twoRules())).isEqualTo("0,(B = 0)(A = 0) -> y,2\n1,(A = 1) -> n,1\n");
	}

	@Test
	public void csvWithMetadataAddsCoverageAndLengthStatistics() {
		assertThat(ToStringParser.buildStringCSVWithMeta(twoRules())).isEqualTo(
			"index,rule,coverage,length\n" +
				"0,(B = 0) & (A = 0) -> (D = y),1,2\n" +
				"1,(A = 1) -> (D = n),1,1\n" +
				"coverage\nmin,avg,max\n1,1.0,1\n" +
				"length\nmin,avg,max\n1,1.5,2\n");
	}

	@Test
	public void indexContinuesAcrossDecisionAttributes() {
		assertThat(ToStringParser.buildStringCSV(ruleSet("A=0 => B=x [1]", "B=x => A=0 [1]")))
			.isEqualTo("0,(A = 0) -> x,1\n1,(B = x) -> 0,1\n");
	}

	@Test
	public void csvDoesNotQuoteValuesContainingCommas() {
		// registry #6
		assertThat(ToStringParser.buildStringCSV(ruleSet("A=a,b => D=y [1]"))).isEqualTo("0,(A = a,b) -> y,1\n");
	}

	@Test
	public void csvWithMetadataFailsWhenThereAreNoRules() {
		// registry #7: min of an empty collection
		Throwable thrown = catchThrowable(() -> ToStringParser.buildStringCSVWithMeta(noRules()));

		assertThat(thrown).isInstanceOf(UnsupportedOperationException.class).hasMessage("empty.min");
	}

	@Test
	public void rsesAndCsvAreEmptyWhenThereAreNoRules() {
		assertThat(ToStringParser.buildStringRSES(noRules())).isEmpty();
		assertThat(ToStringParser.buildStringCSV(noRules())).isEmpty();
	}
}
