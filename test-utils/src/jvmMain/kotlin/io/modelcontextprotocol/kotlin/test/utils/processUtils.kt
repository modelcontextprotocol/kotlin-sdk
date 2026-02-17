package io.modelcontextprotocol.kotlin.test.utils

import org.awaitility.kotlin.await
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Kills any process currently using the specified port.
 *
 * @param port The port number to free up
 */
public fun killProcessOnPort(port: Int) {
    val killCommand = if (isWindows) {
        @Suppress("MaxLineLength")
        listOf(
            "cmd.exe",
            "/c",
            "netstat -ano | findstr :$port | for /f \"tokens=5\" %a in ('more') do taskkill /F /PID %a 2>nul || echo No process found",
        )
    } else {
        listOf("bash", "-c", "lsof -ti:$port | xargs kill -9 2>/dev/null || true")
    }
    ProcessBuilder(killCommand).start().waitFor()
}

/**
 * Waits for a port to become available (indicating server is ready).
 *
 * @param host The hostname to check (default: "localhost")
 * @param port The port number to check
 * @param timeoutSeconds Maximum time to wait in seconds (default: 10)
 * @return true if port became available, false if timeout occurred
 */
public fun waitForPort(host: String = "localhost", port: Int, timeoutSeconds: Long = 10): Boolean = try {
    await.atMost(timeoutSeconds, TimeUnit.SECONDS)
        .pollDelay(200, TimeUnit.MILLISECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until {
            try {
                Socket(host, port).use { true }
            } catch (_: Exception) {
                false
            }
        }
    true
} catch (_: Exception) {
    false
}

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

/**
 * Finds an available port by opening and closing a ServerSocket.
 *
 * @return An available port number
 */
public fun findFreePort(): Int {
    ServerSocket(0).use { socket ->
        return socket.localPort
    }
}

/**
 * Creates a daemon thread that reads and logs process output.
 *
 * @param process The process to read from
 * @param prefix Log prefix for output lines
 * @return The created Thread (already started)
 */
public fun createProcessOutputReader(process: Process, prefix: String): Thread {
    val thread = Thread {
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { println("[$prefix] $it") }
            }
        } catch (e: Exception) {
            println("Warning: Error reading process output: ${e.message}")
        }
    }
    thread.isDaemon = true
    return thread
}

/**
 * Creates a daemon thread that reads and logs process error output.
 *
 * @param process The process to read from
 * @param prefix Log prefix for error lines
 * @return The created Thread (already started)
 */
public fun createProcessErrorReader(process: Process, prefix: String): Thread {
    val thread = Thread {
        try {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { println("[$prefix][err] $it") }
            }
        } catch (e: Exception) {
            println("Warning: Error reading process error stream: ${e.message}")
        }
    }
    thread.isDaemon = true
    return thread
}
