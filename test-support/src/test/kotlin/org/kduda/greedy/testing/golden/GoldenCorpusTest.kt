package org.kduda.greedy.testing.golden

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

class GoldenCorpusTest {
	private val corpus = GoldenCorpus.fromSystemProperties()
	private val cases = corpus.cases()

	@Test
	fun `the manifest has 1260 cases`() {
		cases.size shouldBe 1260
		cases.map { it.id }.toSet().size shouldBe 1260
	}

	@Test
	fun `the failing cases are the three edge inputs in both modes`() {
		val failing = cases.filter { it.error != null }
		failing.size shouldBe 30
		failing.map { it.input }.toSet() shouldBe setOf("edge/dotted-names", "edge/empty-cells", "edge/empty-file")
		failing.map { it.mode }.toSet() shouldBe setOf("dt", "is")
		failing.map { it.heuristic }.toSet() shouldBe setOf("m", "rm", "maxCov", "poly", "log")
		failing.all { it.outputs.isEmpty() } shouldBe true
	}

	@Test
	fun `format errors occur only under csvm`() {
		val withErrors = cases.filter { it.formatErrors.isNotEmpty() }
		withErrors.size shouldBe 340
		withErrors.flatMap { it.formatErrors.keys }.toSet() shouldBe setOf("csvm")
	}

	@Test
	fun `every output named in the manifest exists`() {
		var count = 0
		for (case in cases) {
			for (format in case.outputs.keys) {
				Files.isRegularFile(corpus.output(case, format)!!) shouldBe true
				count++
			}
			if ("csvm" !in case.outputs) corpus.output(case, "csvm") shouldBe null
		}
		count shouldBe 3350 - 0
	}

	@Test
	fun `every nondeterministic input mode has an mcd tie`() {
		val nondeterministic = corpus.nondeterministicInputModes()
		nondeterministic.shouldNotBeEmpty()
		corpus.mcdTies() shouldContainAll nondeterministic
	}

	@Test
	fun `every golden rul file round-trips through the parser`() {
		val outputs = corpus.root.resolve("outputs")
		val files = Files.walk(outputs).use { stream -> stream.filter { it.extension == "rul" }.toList() }
		files.size shouldBe 1230
		for (file in files) {
			val content = file.readText()
			withClue(file.relativeTo(outputs)) { LegacyRul.render(LegacyRul.parse(content)) shouldBe content }
		}
	}

	@Test
	fun `every input reads into a rectangular table`() {
		val inputs = corpus.cases().map { it.input }.distinct()
		inputs.size shouldBe 126
		for (id in inputs.filter { it != "edge/empty-file" }) {
			val table = InputTables.read(corpus.input(id))
			table.columns.shouldNotBeEmpty()
			withClue(id) { table.rows.all { it.size == table.columns.size } shouldBe true }
		}
	}

	@Test
	fun `bom and crlf are dropped, empty cells stay empty, quotes are unescaped`() {
		InputTables.read(corpus.input("edge/bom-crlf")) shouldBe
			InputTable(listOf("a", "d"), listOf(listOf("1", "y"), listOf("2", "n")))
		InputTables.read(corpus.input("edge/empty-cells")).rows.first() shouldBe listOf("", "1", "y")
		InputTables.read(corpus.input("edge/quoted-values")).rows.take(2) shouldBe
			listOf(listOf("x,1", "p q", "y"), listOf("say \"hi\"", "p q", "n"))
	}

	@Test
	fun `an empty file is rejected`() {
		val path = corpus.input("edge/empty-file")
		shouldThrow<IllegalArgumentException> { InputTables.read(path) }.message shouldBe "empty file: $path"
	}
}
