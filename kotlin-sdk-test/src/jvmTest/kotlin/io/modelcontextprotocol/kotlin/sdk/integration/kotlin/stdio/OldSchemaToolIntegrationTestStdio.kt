package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.stdio

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.OldSchemaAbstractToolIntegrationTest

class OldSchemaToolIntegrationTestStdio : OldSchemaAbstractToolIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.STDIO
}
