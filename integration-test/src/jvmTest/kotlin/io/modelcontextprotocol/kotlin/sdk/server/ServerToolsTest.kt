package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ServerToolsTest : AbstractServerFeaturesTest() {

    override fun getServerCapabilities(): ServerCapabilities = ServerCapabilities(tools = ServerCapabilities.Tools())

    @Test
    fun `addTool should accept a non-conforming tool name`() = runTest {
        server.addTool("my invalid tool!", "Tool with non-conforming name") { CallToolResult(emptyList()) }

        client.listTools().tools.single().name shouldBe "my invalid tool!"
    }
}
