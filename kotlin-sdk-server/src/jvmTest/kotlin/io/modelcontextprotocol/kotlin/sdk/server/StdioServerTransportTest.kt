package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.shared.ReadBuffer
import io.modelcontextprotocol.kotlin.sdk.shared.serializeMessage
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.stream.Stream
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val TEST_TIMEOUT = 5.seconds

class StdioServerTransportTest {
    private lateinit var input: PipedInputStream
    private lateinit var inputWriter: PipedOutputStream
    private lateinit var outputBuffer: ReadBuffer
    private lateinit var output: ByteArrayOutputStream

    // We'll store the wrapped streams that meet the constructor requirements
    private lateinit var bufferedInput: Source
    private lateinit var printOutput: Sink

    @BeforeEach
    fun setUp() {
        // Simulate an input stream that we can push data into using inputWriter.
        input = PipedInputStream()
        inputWriter = PipedOutputStream(input)

        outputBuffer = ReadBuffer()

        // A custom ByteArrayOutputStream that appends all written data into outputBuffer
        output = object : ByteArrayOutputStream() {
            override fun write(b: ByteArray, off: Int, len: Int) {
                super.write(b, off, len)
                outputBuffer.append(b.copyOfRange(off, off + len))
            }
        }

        bufferedInput = input.asSource().buffered()
        printOutput = output.asSink().buffered()
    }

    @Test
    fun `should be safe to close before start`() = runTest {
        val server = StdioServerTransport(bufferedInput, printOutput)
        server.close() // initialized guard makes this a no-op; must not throw
    }

    @Test
    fun `should start then close cleanly`() {
        runBlocking {
            withTimeout(TEST_TIMEOUT) {
                val server = StdioServerTransport(bufferedInput, printOutput)
                server.onError { error ->
                    throw error
                }

                var didClose = false
                server.onClose {
                    didClose = true
                }

                server.start()
                assertFalse(didClose, "Should not have closed yet")

                server.close()
                assertTrue(didClose, "Should have closed after calling close()")
            }
        }
    }

    @Test
    fun `should not read until started`() = runTest {
        val server = StdioServerTransport(bufferedInput, printOutput)
        server.onError { error ->
            throw error
        }

        var didRead = false
        val readMessage = CompletableDeferred<JSONRPCMessage>()

        server.onMessage { message ->
            didRead = true
            readMessage.complete(message)
        }

        val message = PingRequest().toJSON()

        // Push a message before the server started
        val serialized = serializeMessage(message)
        inputWriter.write(serialized)
        inputWriter.flush()

        assertFalse(didRead, "Should not have read message before start")

        server.start()
        val received = readMessage.await()
        received shouldBe message
    }

    @Test
    fun `should read multiple messages`() = runTest {
        val server = StdioServerTransport(bufferedInput, printOutput)
        server.onError { error ->
            throw error
        }

        val messages = listOf(
            PingRequest().toJSON(),
            InitializedNotification().toJSON(),
        )

        val readMessages = mutableListOf<JSONRPCMessage>()
        val finished = CompletableDeferred<Unit>()

        server.onMessage { message ->
            readMessages.add(message)
            if (message == messages[1]) {
                finished.complete(Unit)
            }
        }

        // Push both messages before starting the server
        for (m in messages) {
            inputWriter.write(serializeMessage(m))
        }
        inputWriter.flush()

        server.start()
        finished.await()

        readMessages shouldBe messages
    }

    // region: Exception handling

    @ParameterizedTest(name = "[{index}] input throws {0}")
    @MethodSource("inputExceptions")
    fun `should invoke onError when input stream throws`(exception: Exception): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            val server = StdioServerTransport(FaultyRawSource(exception).buffered(), printOutput)
            val capturedError = CompletableDeferred<Throwable>()
            server.onError { capturedError.complete(it) }
            server.onMessage {}

            server.start()

            capturedError.await() shouldBe exception
            server.close()
        }
    }

    @ParameterizedTest(name = "[{index}] output throws {0}")
    @MethodSource("outputExceptions")
    fun `should invoke onError when output sink throws`(exception: Exception): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            val server = StdioServerTransport(bufferedInput, FaultyRawSink(exception).buffered())
            val capturedError = CompletableDeferred<Throwable>()
            server.onError { capturedError.complete(it) }
            server.onMessage {}

            server.start()
            server.send(PingRequest().toJSON())

            capturedError.await() shouldBe exception
            server.close()
        }
    }

    @Test
    fun `should call onClose when input EOF is reached`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            val server = StdioServerTransport(bufferedInput, printOutput)
            val didClose = CompletableDeferred<Unit>()
            server.onError { throw it }
            server.onClose { didClose.complete(Unit) }
            server.onMessage {}

            server.start()
            inputWriter.close() // signal EOF to the reading loop

            eventually(2.seconds) {
                didClose.isCompleted shouldBe true
            }
        }
    }

    @Test
    fun `should throw when starting twice`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            val server = StdioServerTransport(bufferedInput, printOutput)
            server.onMessage {}
            server.start()
            try {
                assertFailsWith<IllegalStateException> {
                    server.start()
                }
            } finally {
                server.close()
            }
        }
    }

    // endregion

    @Test
    fun `should invoke onError when message handler throws`() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            val server = StdioServerTransport(bufferedInput, printOutput)
            val handlerException = RuntimeException("handler failed")
            val capturedError = CompletableDeferred<Throwable>()
            server.onError { capturedError.complete(it) }
            server.onMessage { throw handlerException }

            inputWriter.write(serializeMessage(PingRequest().toJSON()).toByteArray())
            inputWriter.flush()

            server.start()

            capturedError.await() shouldBe handlerException
            server.close()
        }
    }

    @Test
    fun `should continue receiving valid messages after malformed JSON is skipped`() = runBlocking {
        withTimeout(TEST_TIMEOUT) {
            val server = StdioServerTransport(bufferedInput, printOutput)
            val received = CompletableDeferred<JSONRPCMessage>()
            // ReadBuffer silently skips unparseable lines — no onError callback expected
            server.onError {}
            server.onMessage { received.complete(it) }

            val validMessage = PingRequest().toJSON()
            inputWriter.write("not-valid-json\n".toByteArray())
            inputWriter.write(serializeMessage(validMessage).toByteArray())
            inputWriter.flush()

            server.start()

            received.await() shouldBe validMessage
            server.close()
        }
    }

    companion object {
        @JvmStatic
        fun inputExceptions(): Stream<Arguments> = Stream.of(
            Arguments.of(IOException("simulated read failure")),
            Arguments.of(RuntimeException("unexpected read error")),
        )

        @JvmStatic
        fun outputExceptions(): Stream<Arguments> = Stream.of(
            Arguments.of(IOException("simulated write failure")),
            Arguments.of(RuntimeException("unexpected write error")),
        )
    }

    /** A [RawSource] that immediately throws [exception] on every read attempt. */
    private class FaultyRawSource(private val exception: Exception) : RawSource {
        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long = throw exception
        override fun close() {
            // noop
        }
    }

    /** A [RawSink] that throws [exception] on every [write] call. */
    private class FaultyRawSink(private val exception: Exception) : RawSink {
        override fun write(source: Buffer, byteCount: Long): Unit = throw exception
        override fun flush() {
            // noop
        }

        override fun close() {
            // noop
        }
    }
}

fun PipedOutputStream.write(s: String) {
    write(s.toByteArray())
}
