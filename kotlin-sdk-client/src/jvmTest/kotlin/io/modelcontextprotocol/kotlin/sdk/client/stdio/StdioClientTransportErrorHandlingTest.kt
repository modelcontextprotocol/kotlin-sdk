package io.modelcontextprotocol.kotlin.sdk.client.stdio

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport.StderrSeverity.FATAL
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.CONNECTION_CLOSED
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.writeString
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class StdioClientTransportErrorHandlingTest {

    private val request = JSONRPCRequest(id = "test-1", method = "test/method")

    @Test
    fun `should stay open on stderr EOF and close on stdin EOF`(): Unit = runBlocking(Dispatchers.IO) {
        // The pipe keeps stdin open while the empty stderr reaches EOF at once
        val stdinWriter = PipedOutputStream()
        val transport = StdioClientTransport(
            input = PipedInputStream(stdinWriter).asSource().buffered(),
            output = Buffer(),
            error = Buffer(),
        )
        val closed = CompletableDeferred<Unit>()
        transport.onClose { closed.complete(Unit) }

        transport.start()
        delay(200.milliseconds)
        closed.isCompleted.shouldBeFalse()

        // close() cannot interrupt the blocking stdin read, so end the stream instead
        stdinWriter.close()
        withTimeout(5.seconds) { closed.await() }
    }

    @Test
    fun `should report fatal stderr and call onClose exactly once`() = runTest {
        val stdinWriter = PipedOutputStream()
        val transport = StdioClientTransport(
            input = PipedInputStream(stdinWriter).asSource().buffered(),
            output = Buffer(),
            error = Buffer().apply { writeString("FATAL: critical error\n") },
            classifyStderr = { FATAL },
        )
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
        stdinWriter.close() // lets the blocked stdin reader finish so close() can complete
        transport.close()

        closeCount shouldBe 1
        errors.single().shouldBeInstanceOf<McpException>().message shouldContain "FATAL: critical error"
    }

    @Test
    fun `send should map a closed send channel to CONNECTION_CLOSED`() = runTest {
        val transport = startedTransport(sendChannel = Channel<JSONRPCMessage>().apply { close() })

        val exception = shouldThrow<McpException> { transport.send(request) }

        exception.code shouldBe CONNECTION_CLOSED
        exception.cause.shouldBeInstanceOf<ClosedSendChannelException>()
    }

    @Test
    fun `send should propagate cancellation unwrapped`() = runTest {
        val transport = startedTransport(sendChannel = Channel<JSONRPCMessage>().apply { cancel() })

        shouldThrow<CancellationException> { transport.send(request) }
    }

    private suspend fun startedTransport(sendChannel: Channel<JSONRPCMessage>): StdioClientTransport =
        StdioClientTransport(input = Buffer(), output = Buffer(), sendChannel = sendChannel).apply { start() }
}
