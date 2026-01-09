package io.modelcontextprotocol.kotlin.sdk.integration.typescript.stdio

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.OldSchemaAbstractKotlinClientTsServerTest
import io.modelcontextprotocol.kotlin.sdk.integration.typescript.TransportKind
import io.modelcontextprotocol.kotlin.sdk.integration.utils.DisabledOnCI

@DisabledOnCI
class OldSchemaKotlinClientTsServerTestStdio : OldSchemaAbstractKotlinClientTsServerTest() {

    override val transportKind = TransportKind.STDIO

    override suspend fun <T> useClient(block: suspend (Client) -> T): T = withClientStdio { client, proc ->
        try {
            block(client)
        } finally {
            try {
                client.close()
            } catch (_: Exception) {}
            try {
                stopProcess(proc, name = "TypeScript stdio server")
            } catch (_: Exception) {}
        }
    }
}
