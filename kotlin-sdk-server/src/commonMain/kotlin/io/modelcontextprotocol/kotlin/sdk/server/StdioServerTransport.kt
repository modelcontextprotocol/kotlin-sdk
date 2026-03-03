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
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val READ_BUFFER_SIZE = 8192L

/**
 * A server transport that communicates with a client via standard I/O.
 *
 * Reads from input [Source] and writes to output [Sink].
 *
 * Example:
 * ```kotlin
 * val transport = StdioServerTransport {
 *   source = System.`in`.asInput(),
 *   sink =  System.out.asSink(),
 * }
 * ```
 *
 * @constructor Initializes the transport using the provided block for [Configuration].
 * The configuration includes specifying the input and output streams, buffer sizes,
 * and dispatchers for I/O and processing tasks and coroutine scope.
 */
@OptIn(ExperimentalAtomicApi::class)
public class StdioServerTransport(block: Configuration.() -> Unit) : AbstractTransport() {

    /**
     * Configuration for [StdioServerTransport].
     *
     * @property source The input [Source] used to receive data.
     * @property sink The output [Sink] used to send data.
     * @property readBufferSize The buffer size for the read channel.
     * @property readingJobDispatcher The [CoroutineDispatcher] used for reading jobs.
     *      Defaults to [IODispatcher].
     * @property writingJobDispatcher The [CoroutineDispatcher] used for writing jobs.
     *      Defaults to [IODispatcher].
     * @property processingJobDispatcher The [CoroutineDispatcher] used for processing jobs.
     *      Defaults to [Dispatchers.Default].
     * @property readChannelBufferSize The buffer size for the read channel.
     * @property writeChannelBufferSize The buffer size for the write channel.
     * @property coroutineScope The [CoroutineScope] used for managing coroutines.
     */
    @Suppress("LongParameterList")
    public class Configuration internal constructor(
        public var source: Source? = null,
        public var sink: Sink? = null,
        public var readBufferSize: Long = READ_BUFFER_SIZE,
        public var readingJobDispatcher: CoroutineDispatcher = IODispatcher,
        public var writingJobDispatcher: CoroutineDispatcher = IODispatcher,
        public var processingJobDispatcher: CoroutineDispatcher = Dispatchers.Default,
        public var readChannelBufferSize: Int = Channel.UNLIMITED,
        public var writeChannelBufferSize: Int = Channel.UNLIMITED,
        public var coroutineScope: CoroutineScope? = null,
    )

    private val source: Source
    private val sink: Sink
    private val processingJobDispatcher: CoroutineDispatcher
    private val readingJobDispatcher: CoroutineDispatcher
    private val writingJobDispatcher: CoroutineDispatcher
    private val scope: CoroutineScope
    private val readBufferSize: Long
    private val readChannel: Channel<ByteArray>
    private val writeChannel: Channel<JSONRPCMessage>

    init {
        val config = Configuration().apply(block)
        val input = requireNotNull(config.source) { "source is required" }
        val output = requireNotNull(config.sink) { "sink is required" }
        require(config.readBufferSize > 0) { "readBufferSize must be > 0" }

        source = input
        processingJobDispatcher = config.processingJobDispatcher
        readingJobDispatcher = config.readingJobDispatcher
        writingJobDispatcher = config.writingJobDispatcher
        val parentJob = config.coroutineScope?.coroutineContext?.get(Job)
        scope = CoroutineScope(SupervisorJob(parentJob))
        readBufferSize = config.readBufferSize
        readChannel = Channel(config.readChannelBufferSize)
        writeChannel = Channel(config.writeChannelBufferSize)
        sink = output.buffered()
    }

    /**
     * Creates a new instance of [StdioServerTransport]
     * with the given [inputStream] [Source] and [outputStream] [Sink].
     */
    public constructor(inputStream: Source, outputStream: Sink) : this({
        source = inputStream
        sink = outputStream
    })

    private val logger = KotlinLogging.logger {}
    private val readBuffer = ReadBuffer()
    private val initialized: AtomicBoolean = AtomicBoolean(false)

    @Volatile
    private var readingJob: Job? = null

    @Volatile
    private var sendingJob: Job? = null

    @Volatile
    private var processingJob: Job? = null

    override suspend fun start() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
            error("StdioServerTransport already started!")
        }

        // Launch a coroutine to read from stdin
        readingJob = launchReadingJob()

        // Launch a coroutine to process messages from readChannel
        processingJob = launchProcessingJob()

        // Launch a coroutine to handle message sending
        sendingJob = launchSendingJob()
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
        invokeOnCompletion { logJobCompletion("Processing", it) }
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
                readingJob?.cancel(cause)
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
            sendingJob?.cancelAndJoin()

            runCatching {
                source.close()
            }.onFailure { logger.warn(it) { "Failed to close stdin" } }

            readingJob?.cancel()
            readChannel.close()

            processingJob?.cancelAndJoin()

            readBuffer.clear()
            runCatching {
                sink.flush()
                sink.close()
            }.onFailure { logger.warn(it) { "Failed to close stdout" } }

            invokeOnCloseCallback()
        }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        writeChannel.send(message)
    }
}
