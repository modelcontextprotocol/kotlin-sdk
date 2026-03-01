package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.internal.IODispatcher
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.ReadBuffer
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.shared.serializeMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext

/**
 * A server transport that communicates with a client via standard I/O.
 *
 * Reads from System.in and writes to System.out.
 *
 * @constructor Creates a new instance of [StdioServerTransport].
 * @param inputStream The input stream used to receive data.
 * @param outputStream The output stream used to send data.
 */
@OptIn(ExperimentalAtomicApi::class)
public class StdioServerTransport(private val inputStream: Source, outputStream: Sink) : AbstractTransport() {

    private companion object {
        private val logger = KotlinLogging.logger {}
        private const val READ_BUFFER_SIZE = 8192L
    }

    private val readBuffer = ReadBuffer()
    private val initialized: AtomicBoolean = AtomicBoolean(false)
    private var readingJob: Job? = null
    private var sendingJob: Job? = null

    private val coroutineContext: CoroutineContext = IODispatcher + SupervisorJob()
    private val scope = CoroutineScope(coroutineContext)
    private val readChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val writeChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
    private val outputWriter = outputStream.buffered()

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    override suspend fun start() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
            error("StdioServerTransport already started!")
        }

        // Launch a coroutine to read from stdin
        readingJob = launchReadingJob()

        // Launch a coroutine to process messages from readChannel
        launchProcessingJob()

        // Launch a coroutine to handle message sending
        sendingJob = launchSendingJob()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun launchReadingJob(): Job {
        val job = scope.launch {
            val buf = Buffer()
            try {
                while (isActive) {
                    val bytesRead = inputStream.readAtMostTo(buf, READ_BUFFER_SIZE)
                    if (bytesRead == -1L) {
                        // EOF reached
                        break
                    }
                    if (bytesRead > 0) {
                        val chunk = buf.readByteArray()
                        readChannel.send(chunk)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Error reading from stdin" }
                _onError.invoke(e)
            } finally {
                // Reached EOF or error, close connection
                close()
            }
        }
        job.invokeOnCompletion { cause ->
            logger.debug(cause) { "Message reading job completed with cause: $cause" }
        }
        return job
    }

    @Suppress("TooGenericExceptionCaught")
    private fun launchProcessingJob() {
        scope.launch {
            try {
                for (chunk in readChannel) {
                    readBuffer.append(chunk)
                    processReadBuffer()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _onError.invoke(e)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun launchSendingJob(): Job {
        val job = scope.launch {
            try {
                for (message in writeChannel) {
                    val json = serializeMessage(message)
                    outputWriter.writeString(json)
                    outputWriter.flush()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Error writing to stdout" }
                _onError.invoke(e)
            }
        }
        job.invokeOnCompletion { cause ->
            logger.debug(cause) { "Message sending job completed with cause: $cause" }
            if (cause is CancellationException) {
                readingJob?.cancel(cause)
            }
        }
        return job
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun processReadBuffer() {
        while (true) {
            val message = try {
                readBuffer.readMessage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _onError.invoke(e)
                null
            }

            if (message == null) break
            // Async invocation broke delivery order
            try {
                _onMessage.invoke(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _onError.invoke(e)
            }
        }
    }

    override suspend fun close() {
        if (!initialized.compareAndSet(expectedValue = true, newValue = false)) return

        withContext(NonCancellable) {
            writeChannel.close()
            sendingJob?.cancelAndJoin()

            runCatching {
                inputStream.close()
            }.onFailure { logger.warn(it) { "Failed to close stdin" } }

            readingJob?.cancel()

            readChannel.close()
            readBuffer.clear()

            runCatching {
                outputWriter.flush()
                outputWriter.close()
            }.onFailure { logger.warn(it) { "Failed to close stdout" } }

            invokeOnCloseCallback()
        }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        writeChannel.send(message)
    }
}
