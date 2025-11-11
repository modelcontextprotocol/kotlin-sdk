package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.sse

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.OldSchemaAbstractToolIntegrationTest

class OldSchemaToolIntegrationTestSse : OldSchemaAbstractToolIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.SSE
}
