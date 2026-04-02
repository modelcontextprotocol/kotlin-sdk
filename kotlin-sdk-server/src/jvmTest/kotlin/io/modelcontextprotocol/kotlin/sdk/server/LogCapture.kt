package io.modelcontextprotocol.kotlin.sdk.server

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/**
 * Captures log events from the given logger (and its children) using a Logback [ListAppender].
 *
 * Usage:
 * ```
 * LogCapture("io.modelcontextprotocol.kotlin.sdk.server").use { logs ->
 *     // … code that emits log messages …
 *     logs.messages.shouldExist { "expected text" in it }
 * }
 * ```
 */
internal class LogCapture(loggerName: String) : AutoCloseable {
    private val logger: Logger = LoggerFactory.getLogger(loggerName) as Logger
    private val appender: ListAppender<ILoggingEvent> = ListAppender()

    init {
        appender.start()
        logger.addAppender(appender)
    }

    val messages: List<String>
        get() = appender.list.map { it.formattedMessage }

    override fun close() {
        logger.detachAppender(appender)
        appender.stop()
    }
}
