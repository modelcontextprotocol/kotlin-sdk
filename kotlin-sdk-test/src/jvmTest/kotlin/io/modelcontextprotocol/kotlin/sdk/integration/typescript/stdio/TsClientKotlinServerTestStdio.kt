package io.modelcontextprotocol.kotlin.sdk.integration.typescript.stdio

import io.modelcontextprotocol.kotlin.sdk.integration.typescript.AbstractTsClientKotlinServerTest
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TransportKind
import io.modelcontextprotocol.kotlin.sdk.integration.utils.DisabledOnCI

@DisabledOnCI
class TsClientKotlinServerTestStdio : AbstractTsClientKotlinServerTest() {
    override val transportKind = TransportKind.STDIO
    override fun runClient(vararg args: String): String = runStdioClient(*args)
}
