package io.modelcontextprotocol.kotlin.sdk.integration.typescript.http

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.AbstractKotlinClientTsServerEdgeCasesTest
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TransportKind

class KotlinClientTsServerEdgeCasesTestHttp : AbstractKotlinClientTsServerEdgeCasesTest() {

    override val transportKind = TransportKind.HTTP

    private val serverUrl: String by lazy { getSharedHttpUrl() }

    override suspend fun <T> useClient(block: suspend (Client) -> T): T = withClient(serverUrl, block)
}
