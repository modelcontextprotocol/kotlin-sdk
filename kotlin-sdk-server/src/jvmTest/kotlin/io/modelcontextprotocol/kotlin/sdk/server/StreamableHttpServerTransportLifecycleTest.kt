package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.CONNECTION_CLOSED
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class StreamableHttpServerTransportLifecycleTest {

    private fun createTransport(): StreamableHttpServerTransport =
        StreamableHttpServerTransport(StreamableHttpServerTransport.Configuration())

    @Test
    fun `should throw when started twice`() = runTest {
        val transport = createTransport()
        transport.start()

        val exception = shouldThrow<IllegalStateException> {
            transport.start()
        }
        exception.message shouldContain "expected transport state New"
    }

    @Test
    fun `should be idempotent when closed twice`() = runTest {
        val transport = createTransport()
        transport.start()
        transport.close()

        // Second close should not throw
        transport.close()
    }

    @Test
    fun `should throw when sending before start`() = runTest {
        val transport = createTransport()

        val exception = shouldThrow<McpException> {
            transport.send(PingRequest().toJSON())
        }
        exception.code shouldBe CONNECTION_CLOSED
    }
}
