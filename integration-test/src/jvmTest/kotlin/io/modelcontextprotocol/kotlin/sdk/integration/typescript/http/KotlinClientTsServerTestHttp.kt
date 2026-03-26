package io.modelcontextprotocol.kotlin.sdk.integration.typescript.http

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.AbstractKotlinClientTsServerTest
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TransportKind
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class KotlinClientTsServerTestHttp : AbstractKotlinClientTsServerTest() {
    override val transportKind = TransportKind.HTTP

    private val serverUrl: String by lazy { getSharedHttpUrl() }

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
