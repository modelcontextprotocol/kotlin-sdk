package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.streamablehttp

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.AbstractPromptIntegrationTest

class PromptIntegrationTestStreamableHttp : AbstractPromptIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.STREAMABLE_HTTP
}
