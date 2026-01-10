package io.modelcontextprotocol.kotlin.sdk.integration.typescript.sse

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.AbstractKotlinClientTsServerTest
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TransportKind
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class KotlinClientTsServerTestSse : AbstractKotlinClientTsServerTest() {
    override val transportKind = TransportKind.SSE

    private val host = "localhost"
    private val serverUrl: String by lazy { getSharedSseUrl() }

    override suspend fun <T> useClient(block: suspend (Client) -> T): T = withClient(serverUrl) { client ->
        try {
            withTimeout(20.seconds) { block(client) }
        } finally {
            try {
                withTimeout(3.seconds) { client.close() }
            } catch (_: Exception) {}
        }
    }
}
