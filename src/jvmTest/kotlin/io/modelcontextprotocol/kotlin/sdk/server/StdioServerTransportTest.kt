package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.PingRequest
import io.modelcontextprotocol.kotlin.sdk.shared.ReadBuffer
import io.modelcontextprotocol.kotlin.sdk.shared.serializeMessage
import io.modelcontextprotocol.kotlin.sdk.toJSON
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

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
    fun `should start then close cleanly`() {
        runBlocking {
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

    @Test
    fun `should not read until started`() {
        runBlocking {
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
            assertEquals(message, received)
        }
    }

    @Test
    fun `should read multiple messages`() {
        runBlocking {
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

            assertEquals(messages, readMessages)
        }
    }
}

fun PipedOutputStream.write(s: String) {
    write(s.toByteArray())
}
