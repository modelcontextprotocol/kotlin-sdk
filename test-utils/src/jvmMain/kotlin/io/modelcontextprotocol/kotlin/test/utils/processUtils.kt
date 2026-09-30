package io.modelcontextprotocol.kotlin.test.utils

import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Stops the given process.
 *
 * Attempts graceful shutdown first, then forces termination if necessary.
 */
public fun stopProcess(process: Process, wait: Duration = 1.seconds) {
    process.destroy()
    if (!process.waitFor(wait.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
        process.destroyForcibly()
    }
}
