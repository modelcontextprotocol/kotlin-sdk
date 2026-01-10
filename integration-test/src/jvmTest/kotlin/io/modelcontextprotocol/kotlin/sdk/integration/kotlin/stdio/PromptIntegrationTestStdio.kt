package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.stdio

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.AbstractPromptIntegrationTest

class PromptIntegrationTestStdio : AbstractPromptIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.STDIO
}
