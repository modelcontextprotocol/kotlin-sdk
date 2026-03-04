package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.internal.IODispatcher
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.ReadBuffer
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.shared.serializeMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

private const val READ_BUFFER_SIZE = 8192L

/**
 * A server transport that communicates with a client via standard I/O.
 *
 * [StdioServerTransport] manages the communication between a JSON-RPC server and its clients
 * by reading incoming messages from the specified [Source] (input stream) and writing outgoing
 * messages to the [Sink] (output stream).
 *
 * Example:
 * ```kotlin
 * val transport = StdioServerTransport(
 *   source = System.`in`.asInput()
 *   sink =  System.out.asSink()
 * )
 * ```
 *
 * @constructor Creates an instance of [StdioServerTransport] with the specified parameters.
 * @property source The source for reading incoming messages (e.g., stdin or other readable stream).
 * @param sink The sink for writing outgoing messages (e.g., stdout or other writable stream).
 * @property readBufferSize The maximum size of the read buffer, defaults to a pre-configured constant.
 * @property readChannel The channel for receiving raw byte arrays from the input stream.
 * @property writeChannel The channel for sending serialized JSON-RPC messages to the output stream.
 * @property readingJobDispatcher The dispatcher to use for the message-reading coroutine.
 * @property writingJobDispatcher The dispatcher to use for the message-writing coroutine.
 * @property processingJobDispatcher The dispatcher to handle processing of read messages.
 * @param coroutineScope Optional coroutine scope to use for managing internal jobs. A new scope
 *              will be created if not provided.
 */
@OptIn(ExperimentalAtomicApi::class)
@Suppress("LongParameterList")
public class StdioServerTransport(
    private val source: Source,
    sink: Sink,
    private val readBufferSize: Long = READ_BUFFER_SIZE,
    private val readChannel: Channel<ByteArray> = Channel(Channel.UNLIMITED),
    private val writeChannel: Channel<JSONRPCMessage> = Channel(Channel.UNLIMITED),
    private var readingJobDispatcher: CoroutineDispatcher = IODispatcher,
    private var writingJobDispatcher: CoroutineDispatcher = IODispatcher,
    private var processingJobDispatcher: CoroutineDispatcher = Dispatchers.Default,
    coroutineScope: CoroutineScope? = null,
) : AbstractTransport() {

    private val scope: CoroutineScope
    private val sink: Sink

    init {
        require(readBufferSize > 0) { "readBufferSize must be > 0" }
        val parentJob = coroutineScope?.coroutineContext?.get(Job)
        scope = CoroutineScope(SupervisorJob(parentJob))
        this.sink = sink.buffered()
    }

    /**
     * Creates a new instance of [StdioServerTransport]
     * with the given [inputStream] [Source] and [outputStream] [Sink].
     */
    public constructor(inputStream: Source, outputStream: Sink) : this(
        source = inputStream,
        sink = outputStream,
    )

    private val logger = KotlinLogging.logger {}
    private val readBuffer = ReadBuffer()
    private val initialized: AtomicBoolean = AtomicBoolean(false)

    override suspend fun start() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
            error("StdioServerTransport already started!")
        }

        // Launch a coroutine to read from stdin
        launchReadingJob()

        // Launch a coroutine to process messages from readChannel
        launchProcessingJob()

        // Launch a coroutine to handle message sending
        launchSendingJob()
    }

    private fun launchReadingJob(): Job = scope.launch(readingJobDispatcher) {
        val buf = Buffer()
        @Suppress("TooGenericExceptionCaught")
        try {
            while (isActive) {
                val bytesRead = source.readAtMostTo(buf, readBufferSize)
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
        } catch (e: Throwable) {
            logger.error(e) { "Error reading from stdin" }
            _onError.invoke(e)
        } finally {
            // Reached EOF or error, close connection
            close()
        }
    }.apply {
        invokeOnCompletion { logJobCompletion("Message reading", it) }
    }

    private fun launchProcessingJob(): Job = scope.launch(processingJobDispatcher) {
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
    }.apply {
        invokeOnCompletion { cause ->
            logJobCompletion("Processing", cause)
        }
    }

    private fun launchSendingJob(): Job = scope.launch(writingJobDispatcher) {
        @Suppress("TooGenericExceptionCaught")
        try {
            for (message in writeChannel) {
                val json = serializeMessage(message)
                sink.writeString(json)
                sink.flush()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Error writing to stdout" }
            _onError.invoke(e)
        }
    }.apply {
        invokeOnCompletion { cause ->
            logJobCompletion("Message sending", cause)
            if (cause is CancellationException) {
                readChannel.cancel(cause)
            }
        }
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
            // Async invocation broke delivery order
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
            is CancellationException -> {
            }

            null -> {
                logger.debug { "$jobName job completed" }
            }

            else -> {
                logger.debug(cause) { "$jobName job completed exceptionally" }
            }
        }
    }

    override suspend fun close() {
        if (!initialized.compareAndSet(expectedValue = true, newValue = false)) return

        withContext(NonCancellable) {
            writeChannel.close()

            runCatching {
                source.close()
            }.onFailure { logger.warn(it) { "Failed to close stdin" } }

            readChannel.close()

            readBuffer.clear()
            runCatching {
                sink.flush()
                sink.close()
            }.onFailure { logger.warn(it) { "Failed to close stdout" } }

            scope.cancel()
            scope.coroutineContext[Job]?.join()

            invokeOnCloseCallback()
        }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        writeChannel.send(message)
    }
}
