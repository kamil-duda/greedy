package org.kduda.greedy.testing

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.platform.commons.support.AnnotationSupport
import org.opentest4j.AssertionFailedError
import org.opentest4j.TestAbortedException
import java.lang.reflect.Method

/** Runs the tests marked [Pending]: a failure becomes an abort, a pass becomes a failure. */
class PendingExtension : InvocationInterceptor {
	override fun interceptTestMethod(
		invocation: InvocationInterceptor.Invocation<Void?>,
		invocationContext: ReflectiveInvocationContext<Method>,
		extensionContext: ExtensionContext,
	) = runPending(invocation, extensionContext)

	override fun interceptTestTemplateMethod(
		invocation: InvocationInterceptor.Invocation<Void?>,
		invocationContext: ReflectiveInvocationContext<Method>,
		extensionContext: ExtensionContext,
	) = runPending(invocation, extensionContext)

	// Every Throwable the body throws, assertion errors included, makes the test pending; only OutOfMemoryError escapes
	@Suppress("TooGenericExceptionCaught", "ThrowsCount")
	private fun runPending(
		invocation: InvocationInterceptor.Invocation<Void?>,
		context: ExtensionContext,
	) {
		val reason = pendingReason(context)
		if (reason == null) {
			invocation.proceed()
			return
		}
		try {
			invocation.proceed()
		} catch (e: OutOfMemoryError) {
			throw e
		} catch (e: Throwable) {
			throw TestAbortedException("pending: $reason", e)
		}
		throw AssertionFailedError("passes now, remove @Pending(\"$reason\")")
	}

	/** The reason of the nearest [Pending]: on the test method, its class, or an enclosing class. */
	private fun pendingReason(context: ExtensionContext): String? =
		generateSequence(context) { it.parent.orElse(null) }
			.firstNotNullOfOrNull { AnnotationSupport.findAnnotation(it.element, Pending::class.java).orElse(null) }
			?.reason
}
