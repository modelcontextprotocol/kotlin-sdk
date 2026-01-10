package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.stdio

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.AbstractResourceIntegrationTest

class ResourceIntegrationTestStdio : AbstractResourceIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.STDIO
}
