package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.stdio

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.AbstractToolIntegrationTest

class ToolIntegrationTestStdio : AbstractToolIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.STDIO
}
