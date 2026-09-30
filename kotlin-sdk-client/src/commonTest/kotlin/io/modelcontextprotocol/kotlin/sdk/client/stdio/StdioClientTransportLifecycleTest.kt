package io.modelcontextprotocol.kotlin.sdk.client.stdio

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.CONNECTION_CLOSED
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlin.test.Test

class StdioClientTransportLifecycleTest {

    @Test
    fun `should close once without errors on stdin EOF and reject sends after close`() = runTest {
        val transport = StdioClientTransport(input = Buffer(), output = Buffer())
        val errors = mutableListOf<Throwable>()
        var closeCount = 0
        val closed = CompletableDeferred<Unit>()
        transport.onError { errors += it }
        transport.onClose {
            closeCount++
            closed.complete(Unit)
        }

        transport.start()
        closed.await()
        transport.close()

        closeCount shouldBe 1
        errors.shouldBeEmpty()
        shouldThrow<McpException> { transport.send(PingRequest().toJSON()) }.code shouldBe CONNECTION_CLOSED
    }
}
