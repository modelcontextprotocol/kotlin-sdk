package io.modelcontextprotocol.kotlin.sdk.integration.utils

import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.runBlocking
import org.awaitility.kotlin.await

internal fun EmbeddedServer<*, *>.port(): Int {
    var port = 0
    val server = this
    await
        .ignoreExceptions()
        .until {
            port = runBlocking { server.engine.resolvedConnectors().first().port }
            port != 0
        }
    return port
}
