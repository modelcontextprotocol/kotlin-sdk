package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.stdio

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.OldSchemaAbstractResourceIntegrationTest

class OldSchemaResourceIntegrationTestStdio : OldSchemaAbstractResourceIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.STDIO
}
