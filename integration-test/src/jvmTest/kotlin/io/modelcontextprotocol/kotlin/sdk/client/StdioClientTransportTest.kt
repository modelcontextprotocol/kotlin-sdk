package io.modelcontextprotocol.kotlin.sdk.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import io.modelcontextprotocol.kotlin.test.utils.createTeeProcessBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@Timeout(30, unit = TimeUnit.SECONDS)
@DisabledOnOs(OS.WINDOWS) // TODO: fix running on windows
class StdioClientTransportTest {

    @Test
    fun `fatal stderr output should fail connect`(): Unit = runBlocking(Dispatchers.IO) {
        // stderr gets the line only after the client has written `initialize`, and stdin never ends,
        // so only the FATAL classification can fail the connection.
        val stderr = PipedOutputStream()
        val stdin = BlockingRawSource()
        val output = FirstWriteSink {
            stderr.write("simulated error\n".encodeToByteArray())
            stderr.flush()
        }
        val transport = StdioClientTransport(
            input = stdin.buffered(),
            output = output.buffered(),
            error = PipedInputStream(stderr).asSource().buffered(),
            classifyStderr = { StdioClientTransport.StderrSeverity.FATAL },
        )

        val exception = shouldThrow<McpException> {
            withTimeout(5.seconds) { Client(Implementation(name = "test-client", version = "1.0")).connect(transport) }
        }

        exception.code shouldBe RPCError.ErrorCode.CONNECTION_CLOSED
        stdin.close()
        stderr.close()
    }

    @Test
    fun `close should invoke onClose while the process is still running`(): Unit = runBlocking(Dispatchers.IO) {
        val process = createTeeProcessBuilder().start()
        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered(),
        )
        val closed = CompletableDeferred<Unit>()
        transport.onClose { closed.complete(Unit) }
        transport.start()
        closed.isCompleted shouldBe false

        val closing = launch { transport.close() }
        withTimeout(2.seconds) { closed.await() }

        process.destroyForcibly()
        closing.join()
    }

    @Test
    fun `should close cleanly while stdin read is blocked`(): Unit = runBlocking(Dispatchers.IO) {
        val input = BlockingRawSource()
        val transport = StdioClientTransport(input = input.buffered(), output = Buffer())
        val closed = CompletableDeferred<Unit>()
        transport.onClose { closed.complete(Unit) }

        transport.start()
        input.readStarted.await()
        val closeJob = async { transport.close() }
        val closedInTime = withTimeoutOrNull(1.seconds) { closeJob.await() }
        input.close()
        closeJob.await()

        closedInTime.shouldNotBeNull()
        closed.isCompleted shouldBe true
    }

    @Test
    fun `should read messages`(): Unit = runBlocking(Dispatchers.IO) {
        val process = createTeeProcessBuilder().start()
        val transport = StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered(),
        )
        val messages = listOf(PingRequest().toJSON(), InitializedNotification().toJSON())
        val received = Channel<JSONRPCMessage>(Channel.UNLIMITED)
        transport.onMessage { received.send(it) }

        transport.start()
        messages.forEach { transport.send(it) }

        List(messages.size) { received.receive() } shouldBe messages
        transport.close()
        process.destroyForcibly()
    }

    private class BlockingRawSource : RawSource {
        private val closed = CountDownLatch(1)
        val readStarted = CompletableDeferred<Unit>()

        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            readStarted.complete(Unit)
            closed.await()
            return -1L
        }

        override fun close() {
            closed.countDown()
        }
    }

    /** Discards written bytes and calls [onFirstWrite] once. */
    private class FirstWriteSink(private val onFirstWrite: () -> Unit) : RawSink {
        private var written = false

        override fun write(source: Buffer, byteCount: Long) {
            source.skip(byteCount)
            if (!written) {
                written = true
                onFirstWrite()
            }
        }

        override fun flush() = Unit

        override fun close() = Unit
    }
}
