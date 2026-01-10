package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.sse

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.AbstractToolIntegrationTest

class ToolIntegrationTestSse : AbstractToolIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.SSE
}
