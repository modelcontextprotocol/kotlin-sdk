// This file was automatically generated from README.md by Knit tool. Do not edit.
package io.modelcontextprotocol.kotlin.sdk.examples.exampleServerUtilLogging01

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

suspend fun main() {

val server = Server(
    serverInfo = Implementation("example-server", "1.0.0"),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            logging = ServerCapabilities.Logging,
        ),
    )
)

val session = server.createSession(
    StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )
)

session.sendLoggingMessage(
    LoggingMessageNotification(
        LoggingMessageNotificationParams(
            level = LoggingLevel.Info,
            logger = "startup",
            data = buildJsonObject { put("message", "Server started") },
        ),
    ),
)
}
