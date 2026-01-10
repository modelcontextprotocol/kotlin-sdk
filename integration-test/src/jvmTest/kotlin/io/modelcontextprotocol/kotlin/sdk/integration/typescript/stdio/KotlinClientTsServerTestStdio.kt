package io.modelcontextprotocol.kotlin.sdk.integration.typescript.stdio

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.AbstractKotlinClientTsServerTest
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TransportKind

class KotlinClientTsServerTestStdio : AbstractKotlinClientTsServerTest() {

    override val transportKind = TransportKind.STDIO

    override suspend fun <T> useClient(block: suspend (Client) -> T): T = withClientStdio { client, proc ->
        try {
            block(client)
        } finally {
            try {
                client.close()
            } catch (_: Exception) {}
            try {
                stopProcess(proc, name = "TypeScript stdio server")
            } catch (_: Exception) {}
        }
    }
}
