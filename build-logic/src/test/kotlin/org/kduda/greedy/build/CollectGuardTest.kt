package org.kduda.greedy.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

class CollectGuardTest {
	private val dataset = "org/apache/spark/sql/Dataset"
	private val grouped = "org/apache/spark/sql/RelationalGroupedDataset"

	/** A class `className` whose method `run` makes one call at source line 7 (the call's own line). */
	private fun classCalling(
		className: String,
		owner: String,
		name: String,
		desc: String = "()Ljava/lang/Object;",
		methodName: String = "run",
	): Pair<String, ByteArray> {
		val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
		cw.visitSource("X.scala", null)
		val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, methodName, "()V", null, null)
		mv.visitCode()
		val label = Label()
		mv.visitLabel(label)
		mv.visitLineNumber(7, label)
		mv.visitInsn(Opcodes.ACONST_NULL)
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, desc, false)
		mv.visitInsn(Opcodes.POP)
		mv.visitInsn(Opcodes.RETURN)
		mv.visitMaxs(0, 0)
		mv.visitEnd()
		cw.visitEnd()
		return "$className.class" to cw.toByteArray()
	}

	private fun scan(
		cls: Pair<String, ByteArray>,
		allowlist: String = "",
	) = CollectGuard.scan(sequenceOf(cls), parseAllowlist(allowlist)).map { it.message }

	@Test
	fun `a Dataset collect is reported with class, call, source file and line`() {
		val messages = scan(classCalling("a/b/X", dataset, "collect"))
		assertEquals(1, messages.size)
		assertTrue("a.b.X" in messages[0], messages[0])
		assertTrue("Dataset.collect" in messages[0], messages[0])
		assertTrue("X.scala:7" in messages[0], messages[0])
	}

	@ParameterizedTest
	@ValueSource(
		strings = [
			"collect", "collectAsList", "toLocalIterator", "take", "takeAsList", "tail", "head", "first", "show", "reduce",
		],
	)
	fun `every forbidden Dataset method is reported`(method: String) {
		val messages = scan(classCalling("a/X", dataset, method))
		assertEquals(1, messages.size)
		assertTrue("Dataset.$method" in messages[0], messages[0])
	}

	@ParameterizedTest
	@ValueSource(strings = ["org/apache/spark/sql/classic/Dataset", "org/apache/spark/sql/connect/Dataset"])
	fun `the classic and connect Dataset are covered`(owner: String) {
		assertEquals(1, scan(classCalling("a/X", owner, "collect")).size)
	}

	@Test
	fun `pivot with one argument is reported, pivot with values is not`() {
		val one = scan(classCalling("a/X", grouped, "pivot", "(Ljava/lang/String;)L$grouped;"))
		assertEquals(1, one.size)
		assertTrue("RelationalGroupedDataset.pivot" in one[0], one[0])
		val withValues = scan(classCalling("a/X", grouped, "pivot", "(Ljava/lang/String;Ljava/util/List;)L$grouped;"))
		assertEquals(emptyList<String>(), withValues)
	}

	@ParameterizedTest
	@ValueSource(strings = ["count", "groupBy", "cache", "localCheckpoint", "join"])
	fun `harmless Dataset methods are not reported`(method: String) {
		assertEquals(emptyList<String>(), scan(classCalling("a/X", dataset, method)))
	}

	@Test
	fun `a call inside a lambda counts against the top-level class`() {
		val messages = scan(classCalling("a/X\$", dataset, "collect", methodName = "\$anonfun\$run\$1"))
		val lambda = classCalling("a/X\$\$anonfun\$run\$1", dataset, "collect")
		val lambdaMessages = scan(lambda)
		assertEquals(1, messages.size)
		assertTrue(messages[0].startsWith("a.X "), messages[0])
		assertTrue(lambdaMessages[0].startsWith("a.X "), lambdaMessages[0])
	}

	@Test
	fun `an allowlisted call is not reported`() {
		val allowlist = "a.X Dataset.collect  # bounded: one row per heuristic"
		assertEquals(emptyList<String>(), scan(classCalling("a/X", dataset, "collect"), allowlist))
	}

	@Test
	fun `an allowlist entry that matches no call is stale`() {
		val allowlist = "# header\n\na.X Dataset.collect  # reason\n"
		val messages = scan(classCalling("a/X", dataset, "count"), allowlist)
		assertEquals(1, messages.size)
		assertTrue(messages[0].startsWith("stale allowlist entry (line 3)"), messages[0])
	}

	@Test
	fun `an allowlist line without a reason is reported`() {
		val messages = scan(classCalling("a/X", dataset, "collect"), "a.X Dataset.collect")
		assertEquals(listOf("allowlist line 1: reason missing"), messages)
	}

	@Test
	fun `comments and blank lines are ignored by the parser`() {
		assertEquals(
			listOf(AllowEntry("a.X", "Dataset.first", "why", 3)),
			parseAllowlist("# c\n\na.X Dataset.first  # why"),
		)
	}
}
