package org.kduda.greedy.testing

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.testkit.engine.EngineTestKit
import org.junit.platform.testkit.engine.Events
import org.opentest4j.TestAbortedException

/** Runs the fixture classes below (tagged `fixture`, which the Gradle test task excludes) on the Jupiter engine. */
class PendingExtensionTest {
	@Test
	fun `a pending test that throws is aborted with the reason`() {
		val events = run(PendingAndThrows::class.java)

		events.assertStatistics {
			it
				.started(1)
				.aborted(1)
				.failed(0)
				.succeeded(0)
		}
		val thrown = events.aborted().throwables().single()
		thrown.shouldBeInstanceOf<TestAbortedException>()
		thrown.message shouldBe "pending: Phase 4"
		thrown.cause?.message shouldBe "not implemented yet"
	}

	@Test
	fun `a pending test that passes fails and asks for the marker to be removed`() {
		val events = run(PendingAndPasses::class.java)

		events.assertStatistics {
			it
				.started(1)
				.aborted(0)
				.failed(1)
				.succeeded(0)
		}
		events
			.failed()
			.throwables()
			.single()
			.message shouldBe """passes now, remove @Pending("Phase 4")"""
	}

	@Test
	fun `a test without the marker is not affected`() {
		run(Unmarked::class.java).assertStatistics {
			it
				.started(1)
				.aborted(0)
				.failed(0)
				.succeeded(1)
		}
	}

	@Test
	fun `the marker on a class applies to each of its tests`() {
		val events = run(PendingClass::class.java)

		events.assertStatistics {
			it
				.started(2)
				.aborted(1)
				.failed(1)
				.succeeded(0)
		}
		events
			.aborted()
			.throwables()
			.single()
			.message shouldBe "pending: Phase 3"
		events
			.failed()
			.throwables()
			.single()
			.message shouldBe """passes now, remove @Pending("Phase 3")"""
	}

	private fun run(fixture: Class<*>): Events =
		EngineTestKit
			.engine("junit-jupiter")
			.selectors(selectClass(fixture))
			.execute()
			.testEvents()

	private fun Events.throwables(): List<Throwable> =
		stream().map { it.getRequiredPayload(TestExecutionResult::class.java).throwable.orElseThrow() }.toList()

	@Tag("fixture")
	class PendingAndThrows {
		@Test
		@Pending("Phase 4")
		fun throws(): Unit = throw UnsupportedOperationException("not implemented yet")
	}

	@Tag("fixture")
	class PendingAndPasses {
		@Test
		@Pending("Phase 4")
		fun passes() = Unit
	}

	@Tag("fixture")
	class Unmarked {
		@Test
		fun passes() = Unit
	}

	@Tag("fixture")
	@Pending("Phase 3")
	class PendingClass {
		@Test
		fun throws(): Unit = throw UnsupportedOperationException("not implemented yet")

		@Test
		fun passes() = Unit
	}
}
