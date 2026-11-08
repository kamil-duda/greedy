package org.kduda.greedy.testing

import org.junit.jupiter.api.extension.ExtendWith

/**
 * Marks a test that cannot pass yet, e.g. a ported test whose implementation arrives in a later phase. While the test
 * throws, it is reported as aborted with `pending: <reason>`; once it passes, it fails until the marker is removed, so
 * the change that makes it pass also removes the marker. On a class, it applies to every test of the class.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@ExtendWith(PendingExtension::class)
annotation class Pending(
	val reason: String,
)
