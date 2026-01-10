package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.sse

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.AbstractResourceIntegrationTest

class ResourceIntegrationTestSse : AbstractResourceIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.SSE
}
