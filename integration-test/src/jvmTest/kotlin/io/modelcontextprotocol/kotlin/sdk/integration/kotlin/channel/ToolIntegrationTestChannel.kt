package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.channel

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.AbstractToolIntegrationTest

// while this isn't a "production" transport, we still want to ensure that it has the correct behavior
class ToolIntegrationTestChannel : AbstractToolIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.CHANNEL
}
