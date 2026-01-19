package io.modelcontextprotocol.kotlin.test.utils

import io.github.oshai.kotlinlogging.KLogger
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

public fun createTeeProcessBuilder(): ProcessBuilder = if (isWindows) {
    ProcessBuilder("cmd", "/c", "findstr", "^")
} else {
    ProcessBuilder("tee")
}

public fun createSleepyProcessBuilder(): ProcessBuilder = if (isWindows) {
    ProcessBuilder("cmd", "/c", "ping -n 2 127.0.0.1 > nul && echo simulated error 1>&2 && exit 1")
} else {
    ProcessBuilder("sh", "-c", "sleep 1 && echo 'simulated error' >&2 && exit 1")
}

/**
 * Starts logging the lines from this InputStream to a specified logger.
 * Each line from the InputStream is read and logged using the provided logger
 * at the debug level, optionally with an additional prefix string appended
 * to each logged line. The method runs the logging process in a separate daemon thread.
 *
 * @param logger The logger used to log the lines from the InputStream.
 * @param name The name to assign to the daemon thread handling the logging process.
 */
public fun InputStream.startLogging(logger: KLogger, name: String) {
    val stream = this
    Thread {
        try {
            BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    logger.debug { line }
                }
            }
        } catch (e: Exception) {
            logger.trace(e) { "Error reading server test stdout" }
        }
    }.apply {
        this.name = name
        isDaemon = true
    }.start()
}
