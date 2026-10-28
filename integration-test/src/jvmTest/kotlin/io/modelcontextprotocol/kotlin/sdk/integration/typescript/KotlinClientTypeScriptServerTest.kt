package io.modelcontextprotocol.kotlin.sdk.integration.typescript

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.test.utils.TypeScriptRunner
import io.modelcontextprotocol.kotlin.test.utils.runIntegrationTest
import io.modelcontextprotocol.kotlin.test.utils.stopProcess
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Interop with the TypeScript SDK's stdio server (`typescript/server/stdio-server.ts`).
 * Streamable HTTP interop is covered by the conformance suite.
 */
class KotlinClientTypeScriptServerTest {

    @Test
    fun `lists tools and answers ping`() = withTypeScriptServer { client ->
        client.ping()

        client.serverVersion?.name shouldBe "typescript-stdio-server"
        val tool = client.listTools().tools.single()
        tool.name shouldBe "greet"
        tool.description shouldBe "Greets the caller by name"
        tool.inputSchema.required shouldBe listOf("name")
        tool.inputSchema.properties?.get("name")?.jsonObject?.get("type")?.jsonPrimitive?.content shouldBe "string"
    }

    @Test
    fun `calls a tool`() = withTypeScriptServer { client ->
        val result = client.callTool("greet", mapOf("name" to "Kotlin"))

        result.isError shouldNotBe true
        (result.content.single() as TextContent).text shouldBe "Hello, Kotlin!"
    }

    private fun withTypeScriptServer(block: suspend (Client) -> Unit) = runIntegrationTest(timeout = 30.seconds) {
        val process = TypeScriptRunner.start(TYPESCRIPT_DIR, "server/stdio-server.ts")
        val client = Client(Implementation("kotlin-client", "1.0.0"))
        try {
            client.connect(
                StdioClientTransport(
                    input = process.inputStream.asSource().buffered(),
                    output = process.outputStream.asSink().buffered(),
                    error = process.errorStream.asSource().buffered(),
                ),
            )
            block(client)
        } finally {
            client.close()
            stopProcess(process)
        }
    }

    private companion object {
        val TYPESCRIPT_DIR: File = File("src/jvmTest/typescript").absoluteFile
    }
}
