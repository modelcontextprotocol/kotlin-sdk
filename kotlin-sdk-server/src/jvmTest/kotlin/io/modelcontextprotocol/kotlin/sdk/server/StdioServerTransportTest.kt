package io.modelcontextprotocol.kotlin.sdk.server

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.modelcontextprotocol.kotlin.sdk.shared.ReadBuffer
import io.modelcontextprotocol.kotlin.sdk.shared.serializeMessage
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import io.modelcontextprotocol.kotlin.test.utils.runIntegrationTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
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
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
    fun `should be safe to close before start`() = runIntegrationTest {
        val server = StdioServerTransport(bufferedInput, printOutput)
        server.close() // initialized guard makes this a no-op; must not throw
    }

    @Test
    fun `should start then close cleanly`() = runIntegrationTest {
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

    @Test
    fun `should not read until started`() = runIntegrationTest {
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
    fun `should read multiple messages`() = runIntegrationTest {
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
    @MethodSource("inputErrors")
    fun `should invoke onError when input stream throws`(throwable: Throwable): Unit = runIntegrationTest {
        val server = StdioServerTransport(FaultyRawSource(throwable).buffered(), printOutput)
        val capturedError = CompletableDeferred<Throwable>()
        server.onError { capturedError.complete(it) }
        server.onMessage {}

        server.start()

        capturedError.await() shouldBe throwable
        server.close()
    }

    @ParameterizedTest(name = "[{index}] output throws {0}")
    @MethodSource("outputErrors")
    fun `should invoke onError when output sink throws`(throwable: Throwable): Unit = runIntegrationTest {
        val server = StdioServerTransport(bufferedInput, FaultyRawSink(throwable).buffered())
        val capturedError = CompletableDeferred<Throwable>()
        server.onError { capturedError.complete(it) }
        server.onMessage {}

        server.start()
        server.send(PingRequest().toJSON())

        capturedError.await() shouldBe throwable
        server.close()
    }

    @Test
    fun `should call onClose when input EOF is reached`(): Unit = runIntegrationTest {
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

    @Test
    fun `should throw when starting twice`(): Unit = runIntegrationTest {
        val server = StdioServerTransport(bufferedInput, printOutput)
        server.onMessage {}
        server.start()
        withClue("Server should not start twice") {
            shouldThrow<IllegalStateException> {
                server.start()
            }
        }
        server.close()
    }

    @ParameterizedTest(name = "[{index}] handler throws {0}")
    @MethodSource("handlerErrors")
    fun `should continue processing messages after handler throws`(throwable: Throwable) = runIntegrationTest {
        val server = StdioServerTransport(bufferedInput, printOutput)
        val capturedErrors = mutableListOf<Throwable>()
        val receivedMessages = mutableListOf<JSONRPCMessage>()
        val secondMessageProcessed = CompletableDeferred<Unit>()

        val message1 = PingRequest().toJSON()
        val message2 = InitializedNotification().toJSON()

        server.onError { capturedErrors.add(it) }
        server.onMessage { message ->
            if (message == message1) {
                throw throwable
            } else {
                receivedMessages.add(message)
                secondMessageProcessed.complete(Unit)
            }
        }

        server.start()

        inputWriter.write(serializeMessage(message1))
        inputWriter.write(serializeMessage(message2))
        inputWriter.flush()

        secondMessageProcessed.await()

        capturedErrors shouldContain throwable
        receivedMessages shouldBe listOf(message2)
        server.close()
    }

    @Test
    fun `should not invoke onError for CancellationException in handler`() = runIntegrationTest {
        val server = StdioServerTransport(bufferedInput, printOutput)
        val capturedError = CompletableDeferred<Throwable>()
        server.onError { capturedError.complete(it) }

        server.onMessage { throw CancellationException("cancelled") }
        server.start()

        inputWriter.write(serializeMessage(PingRequest().toJSON()))
        inputWriter.flush()

        // We expect onError NOT to be called.
        // We wait a bit to make sure it's not called, then close.
        try {
            withTimeout(1.seconds) {
                capturedError.await()
            }
            fail("Should not have captured an error for CancellationException")
        } catch (_: TimeoutCancellationException) {
            // Success - timeout reached without error captured
        } finally {
            server.close()
        }
    }

    // endregion

    @Test
    fun `should continue receiving valid messages after malformed JSON is skipped`() = runIntegrationTest {
        val server = StdioServerTransport(bufferedInput, printOutput)
        val received = CompletableDeferred<JSONRPCMessage>()
        // ReadBuffer silently skips unparseable lines — no onError callback expected
        server.onError {}
        server.onMessage { received.complete(it) }

        val validMessage = PingRequest().toJSON()
        inputWriter.write("not-valid-json\n".toByteArray())
        inputWriter.write(" \t \r \n".toByteArray()) // blank line
        inputWriter.write(serializeMessage(validMessage).toByteArray())
        inputWriter.flush()

        server.start()

        received.await() shouldBe validMessage
        server.close()
    }

    private fun inputErrors() = listOf(
        IOException("simulated read failure"),
        RuntimeException("unexpected read exception"),
        OutOfMemoryError("unexpected read error"),
    )

    private fun outputErrors() = listOf(
        IOException("simulated write failure"),
        RuntimeException("unexpected write exception"),
        OutOfMemoryError("unexpected write error"),
    )

    private fun handlerErrors() = listOf(
        RuntimeException("handler failure"),
        IOException("handler IO failure"),
        OutOfMemoryError("handler error"),
    )

    /** A [RawSource] that immediately throws [throwable] on every read attempt. */
    private class FaultyRawSource(private val throwable: Throwable) : RawSource {
        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long = throw throwable
        override fun close() {
            // noop
        }
    }

    /** A [RawSink] that throws [throwable] on every [write] call. */
    private class FaultyRawSink(private val throwable: Throwable) : RawSink {
        override fun write(source: Buffer, byteCount: Long): Unit = throw throwable
        override fun flush() {
            // noop
        }

        override fun close() {
            // noop
        }
    }
}

private fun PipedOutputStream.write(s: String) {
    write(s.toByteArray())
}
