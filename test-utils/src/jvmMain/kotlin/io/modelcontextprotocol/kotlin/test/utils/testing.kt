package io.modelcontextprotocol.kotlin.test.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val defaultTestDispatcher: CoroutineContext = Dispatchers.IO.limitedParallelism(100)
private val defaultTestTimeout: Duration = 5.seconds

/**
 * Executes a suspendable integration test block within a coroutine scope, applying a timeout constraint.
 *
 * @param timeout The maximum duration allowed for the test execution. Defaults to 5 seconds.
 * @param context The coroutine context within which the test block is executed.
 *          Defaults to [Dispatchers.IO] with 100 parallel tasks.
 * @param block The suspendable test logic to be executed.
 */
public fun runIntegrationTest(
    context: CoroutineContext = defaultTestDispatcher,
    timeout: Duration = defaultTestTimeout,
    block: suspend CoroutineScope.() -> Unit,
): Unit = runBlocking(context) {
    withTimeout(timeout, block)
}
