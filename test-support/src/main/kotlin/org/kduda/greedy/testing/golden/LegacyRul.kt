package org.kduda.greedy.testing.golden

data class LegacyCondition(
	val attribute: String,
	val value: String,
)

data class LegacyRule(
	val conditions: List<LegacyCondition>,
	val decisionAttribute: String,
	val decision: String,
	val support: Long,
)

/**
 * Parser and renderer for the legacy RSES rule lines, `(a=v)&(b=w)=>(dec=x[s]) s`. Values are kept verbatim, so
 * they may contain `=`, `(`, `)`, `"`, `,` and spaces; only the structure around them is interpreted.
 */
object LegacyRul {
	private const val DECISION_START = "=>("
	private const val CONDITION_SEPARATOR = ")&("
	private const val SUPPORT_END = "]) "

	fun parse(content: String): List<LegacyRule> = content.split("\n").filter { it.isNotEmpty() }.map(::parseLine)

	fun renderLine(rule: LegacyRule): String {
		val conditions = rule.conditions.joinToString(CONDITION_SEPARATOR) { "${it.attribute}=${it.value}" }
		val body = if (rule.conditions.isEmpty()) "" else "($conditions)"
		return "$body$DECISION_START${rule.decisionAttribute}=${rule.decision}[${rule.support}]) ${rule.support}"
	}

	fun render(rules: List<LegacyRule>): String = rules.joinToString("") { renderLine(it) + "\n" }

	private fun parseLine(line: String): LegacyRule {
		val arrow = line.lastIndexOf(DECISION_START)
		require(arrow >= 0) { "no decision part: $line" }
		val decisionPart = line.substring(arrow + DECISION_START.length)
		val equals = decisionPart.indexOf('=')
		val bracket = decisionPart.lastIndexOf('[')
		require(equals in 0 until bracket) { "malformed decision: $line" }
		val close = decisionPart.indexOf(SUPPORT_END, bracket)
		require(close >= 0) { "malformed support: $line" }
		val support = decisionPart.substring(bracket + 1, close).toLongOrNull()
		val trailing = decisionPart.substring(close + SUPPORT_END.length).toLongOrNull()
		require(support != null && support == trailing) { "support mismatch: $line" }
		return LegacyRule(
			conditions = parseConditions(line.substring(0, arrow), line),
			decisionAttribute = decisionPart.substring(0, equals),
			decision = decisionPart.substring(equals + 1, bracket),
			support = support,
		)
	}

	private fun parseConditions(
		text: String,
		line: String,
	): List<LegacyCondition> {
		if (text.isEmpty()) return emptyList()
		require(text.startsWith("(") && text.endsWith(")")) { "malformed conditions: $line" }
		return text.substring(1, text.length - 1).split(CONDITION_SEPARATOR).map {
			val equals = it.indexOf('=')
			require(equals >= 0) { "malformed condition: $line" }
			LegacyCondition(it.substring(0, equals), it.substring(equals + 1))
		}
	}
}
