package io.modelcontextprotocol.kotlin.sdk.integration.typescript.stdio

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.AbstractKotlinClientTsServerEdgeCasesTest
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TransportKind

class KotlinClientTsServerEdgeCasesTestStdio : AbstractKotlinClientTsServerEdgeCasesTest() {

    override val transportKind = TransportKind.STDIO

    override suspend fun <T> useClient(block: suspend (Client) -> T): T = withClientStdio { c, _ -> block(c) }
}
