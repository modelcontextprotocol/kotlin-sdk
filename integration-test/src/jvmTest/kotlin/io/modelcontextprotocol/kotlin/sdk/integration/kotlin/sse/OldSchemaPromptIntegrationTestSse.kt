package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.sse

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.OldSchemaAbstractPromptIntegrationTest

class OldSchemaPromptIntegrationTestSse : OldSchemaAbstractPromptIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.SSE
}
