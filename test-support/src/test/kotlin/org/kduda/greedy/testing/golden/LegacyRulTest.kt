package org.kduda.greedy.testing.golden

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LegacyRulTest {
	private fun parseOne(line: String): LegacyRule = LegacyRul.parse(line + "\n").single()

	private fun roundTrip(line: String) {
		LegacyRul.render(LegacyRul.parse(line + "\n")) shouldBe line + "\n"
		LegacyRul.renderLine(parseOne(line)) shouldBe line
	}

	@Test
	fun `parses conditions in order, decision and support`() {
		parseOne("(B=0)&(A=0)=>(D=y[1]) 1") shouldBe
			LegacyRule(listOf(LegacyCondition("B", "0"), LegacyCondition("A", "0")), "D", "y", 1)
		roundTrip("(B=0)&(A=0)=>(D=y[1]) 1")
	}

	@Test
	fun `keeps quotes verbatim`() {
		val rule = parseOne("(a=\"say \"\"hi\"\"\")=>(d=n[1]) 1")
		rule.conditions shouldBe listOf(LegacyCondition("a", "\"say \"\"hi\"\"\""))
		roundTrip("(a=\"say \"\"hi\"\"\")=>(d=n[1]) 1")
	}

	@Test
	fun `keeps commas and spaces`() {
		parseOne("(a=x,1)&(b=p q)=>(d=y[2]) 2").conditions shouldBe
			listOf(LegacyCondition("a", "x,1"), LegacyCondition("b", "p q"))
		roundTrip("(a=x,1)&(b=p q)=>(d=y[2]) 2")
	}

	@Test
	fun `keeps parentheses and equals signs in values`() {
		parseOne("(a=f(x))&(b=1=2)=>(d=y[1]) 1").conditions shouldBe
			listOf(LegacyCondition("a", "f(x)"), LegacyCondition("b", "1=2"))
		roundTrip("(a=f(x))&(b=1=2)=>(d=y[1]) 1")
	}

	@Test
	fun `rejects a support that differs from the trailing support`() {
		shouldThrow<IllegalArgumentException> { LegacyRul.parse("(a=1)=>(d=y[1]) 2\n") }
	}

	@Test
	fun `empty content has no rules`() {
		LegacyRul.parse("").shouldBeEmpty()
		LegacyRul.render(emptyList()) shouldBe ""
	}
}
