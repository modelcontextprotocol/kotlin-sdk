package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.internal.IODispatcher
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
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext

private const val READ_BUFFER_SIZE = 8192L

/**
 * A server transport that communicates with a client via standard I/O.
 *
 * Reads from input [Source] and writes to output [Sink].
 *
 * @constructor Creates a new instance of [StdioServerTransport].
 * @param inputStream The input [Source] used to receive data.
 * @param outputStream The output [Sink] used to send data.
 */
@OptIn(ExperimentalAtomicApi::class)
public class StdioServerTransport(private val inputStream: Source, outputStream: Sink) : AbstractServerTransport() {

    override val logger: KLogger = KotlinLogging.logger {}
    private val readBuffer = ReadBuffer()
    private var readingJob: Job? = null
    private var sendingJob: Job? = null
    private var processingJob: Job? = null

    private val coroutineContext: CoroutineContext = IODispatcher + SupervisorJob()
    private val scope = CoroutineScope(coroutineContext)
    private val readChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private val writeChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
    private val outputSink = outputStream.buffered()

    override suspend fun initialize() {
        readingJob = launchReadingJob()
        processingJob = launchProcessingJob()
        sendingJob = launchSendingJob()
    }

    private fun launchReadingJob(): Job {
        val job = scope.launch {
            val buf = Buffer()
            @Suppress("TooGenericExceptionCaught")
            try {
                while (isActive) {
                    val bytesRead = inputStream.readAtMostTo(buf, READ_BUFFER_SIZE)
                    if (bytesRead == -1L) {
                        break
                    }
                    if (bytesRead > 0) {
                        val chunk = buf.readByteArray()
                        readChannel.send(chunk)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error(e) { "Error reading from stdin" }
                _onError.invoke(e)
            } finally {
                close()
            }
        }
        job.invokeOnCompletion { cause ->
            logJobCompletion("Message reading", cause)
        }
        return job
    }

    private fun launchProcessingJob(): Job {
        val job = scope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                for (chunk in readChannel) {
                    readBuffer.append(chunk)
                    processReadBuffer()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _onError.invoke(e)
            }
        }
        job.invokeOnCompletion { cause ->
            logJobCompletion("Processing", cause)
        }
        return job
    }

    private fun launchSendingJob(): Job {
        val job = scope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                for (message in writeChannel) {
                    val json = serializeMessage(message)
                    outputSink.writeString(json)
                    outputSink.flush()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error(e) { "Error writing to stdout" }
                _onError.invoke(e)
            }
        }
        job.invokeOnCompletion { cause ->
            logJobCompletion("Message sending", cause)
            if (cause is CancellationException) {
                readingJob?.cancel(cause)
            }
        }
        return job
    }

    private suspend fun processReadBuffer() {
        @Suppress("TooGenericExceptionCaught")
        while (true) {
            val message = try {
                readBuffer.readMessage()
            } catch (e: Throwable) {
                _onError.invoke(e)
                null
            }

            if (message == null) break
            try {
                _onMessage.invoke(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error(e) { "Error processing message" }
                _onError.invoke(e)
            }
        }
    }

    private fun logJobCompletion(jobName: String, cause: Throwable?) {
        when (cause) {
            is CancellationException -> {}
            null -> logger.debug { "$jobName job completed" }
            else -> logger.debug(cause) { "$jobName job completed exceptionally" }
        }
    }

    override suspend fun closeResources() {
        withContext(NonCancellable) {
            writeChannel.close()
            sendingJob?.cancelAndJoin()

            runCatching {
                inputStream.close()
            }.onFailure { logger.warn(it) { "Failed to close stdin" } }

            readingJob?.cancel()
            readChannel.close()

            processingJob?.cancelAndJoin()

            readBuffer.clear()

            runCatching {
                outputSink.flush()
                outputSink.close()
            }.onFailure { logger.warn(it) { "Failed to close stdout" } }
        }
    }

    override suspend fun performSend(message: JSONRPCMessage, options: TransportSendOptions?) {
        writeChannel.send(message)
    }
}
