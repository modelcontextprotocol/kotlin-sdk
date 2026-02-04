package io.modelcontextprotocol.kotlin.sdk.integration.typescript.stdio

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.AbstractKotlinClientTsServerTest
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TransportKind

class KotlinClientTsServerTestStdio : AbstractKotlinClientTsServerTest() {

    override val transportKind = TransportKind.STDIO

    override suspend fun <T> useClient(block: suspend (Client) -> T): T =
        // TsTestBase.withClientStdio already handles client.close() + process shutdown.
        withClientStdio { client, _ -> block(client) }
}
