package io.modelcontextprotocol.kotlin.sdk.integration.kotlin.stdio

import io.modelcontextprotocol.kotlin.sdk.integration.kotlin.OldSchemaAbstractPromptIntegrationTest

class OldSchemaPromptIntegrationTestStdio : OldSchemaAbstractPromptIntegrationTest() {
    override val transportKind: TransportKind = TransportKind.STDIO
}
