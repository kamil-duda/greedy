package org.kduda.greedy.testing.golden

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.kduda.greedy.testing.golden.GoldenFiles.normalize

class GoldenFilesTest {
	private val stats = "coverage\nmin,avg,max\n2,2.0,2\nlength\nmin,avg,max\n1,1.0,1\n"

	@Test
	fun `rules file ignores line order`() {
		normalize("x.rul", "(A=1)=>(D=n[2]) 2\n(A=0)=>(D=y[2]) 2\n") shouldBe
			normalize("x.rul", "(A=0)=>(D=y[2]) 2\n(A=1)=>(D=n[2]) 2\n")
	}

	@Test
	fun `csv ignores line order and index`() {
		normalize("x.csv", "0,(A = 1) -> n,1\n1,(A = 0) -> y,1\n") shouldBe
			normalize("x.csv", "0,(A = 0) -> y,1\n1,(A = 1) -> n,1\n")
	}

	@Test
	fun `csv index must run from zero across all rules`() {
		normalize("x.csv", "0,(A = 1) -> n,1\n0,(B = 0) -> y,1\n") shouldNotBe
			normalize("x.csv", "0,(A = 1) -> n,1\n1,(B = 0) -> y,1\n")
	}

	@Test
	fun `csv with metadata index must run from zero across all rules`() {
		val header = "index,rule,coverage,length\n"
		normalize("x-wmetadata.csv", header + "1,(A = 1) -> (D = n),2,1\n2,(B = 0) -> (E = y),2,1\n" + stats) shouldNotBe
			normalize("x-wmetadata.csv", header + "0,(A = 1) -> (D = n),2,1\n1,(B = 0) -> (E = y),2,1\n" + stats)
	}

	@Test
	fun `csv with different rules differs`() {
		normalize("x.csv", "0,(A = 1) -> n,1\n") shouldNotBe normalize("x.csv", "0,(A = 1) -> y,1\n")
	}

	@Test
	fun `csv with metadata compares statistics verbatim`() {
		val rules = "index,rule,coverage,length\n0,(A = 1) -> (D = n),2,1\n1,(A = 0) -> (D = y),2,1\n"
		val head = "coverage\nmin,avg,max\n2,2.0,2\nlength\nmin,avg,max\n"
		normalize("x-wmetadata.csv", rules + head + "1,1.0,1\n") shouldNotBe
			normalize("x-wmetadata.csv", rules + head + "1,1.5,2\n")
	}

	@Test
	fun `csv with metadata ignores rule order and index`() {
		val header = "index,rule,coverage,length\n"
		normalize("x-wmetadata.csv", header + "0,(A = 1) -> (D = n),2,1\n1,(A = 0) -> (D = y),2,1\n" + stats) shouldBe
			normalize("x-wmetadata.csv", header + "0,(A = 0) -> (D = y),2,1\n1,(A = 1) -> (D = n),2,1\n" + stats)
	}
}
