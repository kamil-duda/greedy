package org.kduda.greedy.golden;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kduda.greedy.golden.GoldenFiles.normalize;

public class GoldenFilesTest {

	@Test
	public void rulesFileIgnoresLineOrder() {
		assertThat(normalize("x.rul", "(A=1)=>(D=n[2]) 2\n(A=0)=>(D=y[2]) 2\n"))
			.isEqualTo(normalize("x.rul", "(A=0)=>(D=y[2]) 2\n(A=1)=>(D=n[2]) 2\n"));
	}

	@Test
	public void csvIgnoresLineOrderAndIndex() {
		assertThat(normalize("x.csv", "0,(A = 1) -> n,1\n1,(A = 0) -> y,1\n"))
			.isEqualTo(normalize("x.csv", "0,(A = 0) -> y,1\n1,(A = 1) -> n,1\n"));
	}

	@Test
	public void csvIndexMustRunFromZeroAcrossAllRules() {
		// legacy numbers rules 0..n-1 across all decision tables (PLAN.md, "Index runs across all decision tables")
		assertThat(normalize("x.csv", "0,(A = 1) -> n,1\n0,(B = 0) -> y,1\n"))
			.isNotEqualTo(normalize("x.csv", "0,(A = 1) -> n,1\n1,(B = 0) -> y,1\n"));
	}

	@Test
	public void csvWithMetadataIndexMustRunFromZeroAcrossAllRules() {
		String stats = "coverage\nmin,avg,max\n2,2.0,2\nlength\nmin,avg,max\n1,1.0,1\n";

		assertThat(normalize("x-wmetadata.csv", "index,rule,coverage,length\n1,(A = 1) -> (D = n),2,1\n2,(B = 0) -> (E = y),2,1\n" + stats))
			.isNotEqualTo(normalize("x-wmetadata.csv", "index,rule,coverage,length\n0,(A = 1) -> (D = n),2,1\n1,(B = 0) -> (E = y),2,1\n" + stats));
	}

	@Test
	public void csvWithDifferentRulesDiffers() {
		assertThat(normalize("x.csv", "0,(A = 1) -> n,1\n"))
			.isNotEqualTo(normalize("x.csv", "0,(A = 1) -> y,1\n"));
	}

	@Test
	public void csvWithMetadataComparesStatisticsVerbatim() {
		String rules = "index,rule,coverage,length\n0,(A = 1) -> (D = n),2,1\n1,(A = 0) -> (D = y),2,1\n";
		String stats = "coverage\nmin,avg,max\n2,2.0,2\nlength\nmin,avg,max\n";

		assertThat(normalize("x-wmetadata.csv", rules + stats + "1,1.0,1\n"))
			.isNotEqualTo(normalize("x-wmetadata.csv", rules + stats + "1,1.5,2\n"));
	}

	@Test
	public void csvWithMetadataIgnoresRuleOrderAndIndex() {
		String stats = "coverage\nmin,avg,max\n2,2.0,2\nlength\nmin,avg,max\n1,1.0,1\n";

		assertThat(normalize("x-wmetadata.csv", "index,rule,coverage,length\n0,(A = 1) -> (D = n),2,1\n1,(A = 0) -> (D = y),2,1\n" + stats))
			.isEqualTo(normalize("x-wmetadata.csv", "index,rule,coverage,length\n0,(A = 0) -> (D = y),2,1\n1,(A = 1) -> (D = n),2,1\n" + stats));
	}
}
