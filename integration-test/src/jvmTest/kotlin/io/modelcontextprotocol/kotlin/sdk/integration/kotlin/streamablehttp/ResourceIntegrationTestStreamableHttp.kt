package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.streamablehttp

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.AbstractResourceIntegrationTest

class ResourceIntegrationTestStreamableHttp : AbstractResourceIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.STREAMABLE_HTTP
}
