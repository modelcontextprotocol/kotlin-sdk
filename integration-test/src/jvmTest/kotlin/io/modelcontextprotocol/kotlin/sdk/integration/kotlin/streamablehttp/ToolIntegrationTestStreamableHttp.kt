package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.streamablehttp

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.AbstractToolIntegrationTest

class ToolIntegrationTestStreamableHttp : AbstractToolIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.STREAMABLE_HTTP
}
