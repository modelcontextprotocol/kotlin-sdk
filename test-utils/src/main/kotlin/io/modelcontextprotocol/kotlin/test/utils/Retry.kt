package io.modelcontextprotocol.kotlin.test.utils

import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.opentest4j.TestAbortedException
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Optional

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(RetryExtension::class)
public annotation class Retry(val times: Int = 3, val delayMs: Long = 1000)

public class RetryExtension : InvocationInterceptor {
    override fun interceptTestMethod(
        invocation: Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        executeWithRetry(invocation, extensionContext)
    }

    private fun resolveRetryAnnotation(extensionContext: ExtensionContext): Retry? {
        val classAnn = extensionContext.testClass.flatMap { findRetry(it) }
        return classAnn.orElse(null)
    }

    private fun findRetry(element: AnnotatedElement): Optional<Retry> =
        Optional.ofNullable(element.getAnnotation(Retry::class.java))

    @Suppress("ThrowsCount", "CyclomaticComplexMethod")
    private fun executeWithRetry(invocation: Invocation<Void>, extensionContext: ExtensionContext) {
        val retry = resolveRetryAnnotation(extensionContext)
        if (retry == null || retry.times <= 1) {
            invocation.proceed()
            return
        }

        val maxAttempts = retry.times
        val delay = retry.delayMs
        var lastError: Throwable? = null

        for (attempt in 1..maxAttempts) {
            if (attempt > 1 && delay > 0) {
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }

            try {
                if (attempt == 1) {
                    invocation.proceed()
                } else {
                    val instance = extensionContext.requiredTestInstance
                    val testMethod = extensionContext.requiredTestMethod
                    testMethod.isAccessible = true
                    testMethod.invoke(instance)
                }
                return
            } catch (t: Throwable) {
                if (t is TestAbortedException) throw t
                lastError = if (t is InvocationTargetException) t.targetException ?: t else t
                if (attempt == maxAttempts) {
                    println(
                        "[Retry] Giving up after $attempt attempts for ${
                            describeTest(
                                extensionContext,
                            )
                        }: ${lastError.message}",
                    )
                    throw lastError
                }
                println(
                    "[Retry] Failure on attempt $attempt/$maxAttempts for ${
                        describeTest(
                            extensionContext,
                        )
                    }: ${lastError.message}",
                )
            }
        }

        throw lastError ?: IllegalStateException("Unexpected state in retry logic")
    }

    private fun describeTest(ctx: ExtensionContext): String {
        val methodName = ctx.testMethod.map(Method::getName).orElse("<unknown>")
        val className = ctx.testClass.map { it.name }.orElse("<unknown>")
        return "$className#$methodName"
    }
}
