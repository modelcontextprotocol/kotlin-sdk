package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.sse

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.AbstractPromptIntegrationTest

class PromptIntegrationTestSse : AbstractPromptIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.SSE
}
