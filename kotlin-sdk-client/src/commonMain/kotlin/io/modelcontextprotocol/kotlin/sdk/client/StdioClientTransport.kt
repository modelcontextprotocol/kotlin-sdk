package io.modelcontextprotocol.kotlin.sdk.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport.StderrSeverity.DEBUG
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport.StderrSeverity.FATAL
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport.StderrSeverity.IGNORE
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport.StderrSeverity.INFO
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport.StderrSeverity.WARNING
import io.modelcontextprotocol.kotlin.sdk.internal.IODispatcher
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.ReadBuffer
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.shared.serializeMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.CONNECTION_CLOSED
import io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.INTERNAL_ERROR
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import kotlinx.serialization.SerializationException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmOverloads

/**
 * A transport implementation for JSON-RPC communication over standard I/O streams.
 *
 * Reads JSON-RPC messages from [input] and writes messages to [output]. Optionally monitors
 * [error] stream for stderr output with configurable severity handling.
 *
 * ## Usage Example
 * ```kotlin
 * val process = ProcessBuilder("mcp-server").start()
 *
 * val transport = StdioClientTransport(
 *     input = process.inputStream.asSource().buffered(),
 *     output = process.outputStream.asSink().buffered(),
 *     error = process.errorStream.asSource().buffered()
 * ) { stderrLine ->
 *     when {
 *         stderrLine.contains("error", ignoreCase = true) -> StderrSeverity.FATAL
 *         stderrLine.contains("warning", ignoreCase = true) -> StderrSeverity.WARNING
 *         else -> StderrSeverity.INFO
 *     }
 * }
 *
 * transport.start()
 * ```
 *
 * @param input The input stream where messages are received.
 * @param output The output stream where messages are sent.
 * @param error Optional error stream for stderr monitoring.
 * @param sendChannel Channel for outbound messages. Default: buffered channel (capacity 64).
 * @param classifyStderr Callback to classify stderr lines. Return [StderrSeverity.FATAL] to fail transport,
 *                       or [StderrSeverity.WARNING] / [StderrSeverity.INFO] / [StderrSeverity.DEBUG]
 *                       to log, or [StderrSeverity.IGNORE] to discard.
 *                       Default value: [StderrSeverity.DEBUG].
 * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#stdio">MCP Specification</a>
 */
@OptIn(ExperimentalAtomicApi::class)
public class StdioClientTransport @JvmOverloads public constructor(
    private val input: Source,
    private val output: Sink,
    private val error: Source? = null,
    private val sendChannel: Channel<JSONRPCMessage> = Channel(Channel.BUFFERED),
    private val classifyStderr: (String) -> StderrSeverity = { DEBUG },
) : AbstractTransport() {

    private companion object {
        /**
         * Buffer size for I/O operations.
         * 8KB is optimal for most systems (matches default page size).
         */
        const val BUFFER_SIZE = 8 * 1024L

        private val logger = KotlinLogging.logger {}
    }

    /**
     * Severity classification for stderr messages.
     *
     * - [FATAL]: Calls error handler and terminates transport.
     * - [WARNING]: Logs at WARN level, transport continues.
     * - [INFO]: Logs at INFO level, transport continues.
     * - [DEBUG]: Logs at DEBUG level, transport continues.
     * - [IGNORE]: Discards message silently, transport continues.
     */
    public enum class StderrSeverity { FATAL, WARNING, INFO, DEBUG, IGNORE }

    private val ioCoroutineContext: CoroutineContext = IODispatcher
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val initialized = AtomicBoolean(false)
    private val onCloseCalled = AtomicBoolean(false)

    override suspend fun start() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
            error("StdioClientTransport already started!")
        }
        logger.debug { "Starting StdioClientTransport..." }

        // Producers run on IODispatcher for I/O
        // Collector runs on Default dispatcher for message handling
        scope.launch(CoroutineName("StdioClientTransport.IO#${hashCode()}")) {
            var writeJob: Job? = null
            val mainScope = this
            try {
                // Explicitly use ioCoroutineContext for I/O operations
                writeJob = launch(ioCoroutineContext) {
                    logger.debug { "Write coroutine started." }
                    output.buffered().use { sink ->
                        sendChannel.consumeEach { message ->
                            sendOutboundMessage(message, sink, mainScope)
                            yield() // Giving other coroutines a chance to run
                        }
                    }
                }

                val eventsFlow = channelFlow {
                    launch(ioCoroutineContext) {
                        logger.debug { "Read stdin coroutine started." }
                        val readBuffer = ReadBuffer() // parses bytes into JSONRPCMessage
                        readSource(stream = ProcessStream.Stdin, source = input, channel = this@channelFlow) { bytes ->
                            readBuffer.append(bytes)
                            do {
                                val msg = readBuffer.readMessage()
                                msg?.let { send(Event.JsonRpc(msg)) }
                            } while (msg != null)
                        }
                    }.invokeOnCompletion {
                        logger.debug(it) { "Read stdin coroutine finished." }
                    }

                    error?.let { source ->
                        launch(ioCoroutineContext) {
                            logger.debug { "Read stderr coroutine started." }
                            readSource(
                                stream = ProcessStream.Stderr,
                                source = source,
                                channel = this@channelFlow,
                            ) { bytes ->
                                val str = bytes.decodeToString()
                                send(Event.StderrEvent(str))
                            }
                        }
                    }
                }

                // Collect events on handlerCoroutineContext (Dispatchers.Default from parent scope)
                // No flowOn necessary - collection runs in parent launch context
                eventsFlow
                    .collect { event ->
                        when (event) {
                            is Event.JsonRpc -> {
                                handleJSONRPCMessage(event.message)
                            }

                            is Event.StderrEvent -> {
                                val errorSeverity = classifyStderr(event.message)
                                when (errorSeverity) {
                                    FATAL -> {
                                        runCatching {
                                            _onError(
                                                McpException(INTERNAL_ERROR, "Message in StdErr: ${event.message}"),
                                            )
                                        }
                                        stopProcessing("Fatal STDERR message received")
                                    }

                                    WARNING -> {
                                        logger.warn { "STDERR message received: ${event.message}" }
                                    }

                                    INFO -> {
                                        logger.info { "STDERR message received: ${event.message}" }
                                    }

                                    DEBUG -> {
                                        logger.debug { "STDERR message received: ${event.message}" }
                                    }

                                    IGNORE -> {
                                        // do nothing
                                    }
                                }
                            }

                            is Event.EOFEvent -> {
                                if (event.stream == ProcessStream.Stdin) {
                                    stopProcessing("EOF in ${event.stream}")
                                }
                            }

                            is Event.IOErrorEvent -> {
                                runCatching { _onError(event.cause) }
                                stopProcessing("IO Error", event.cause)
                            }
                        }
                    }
            } finally {
                // Wait for write job to complete before closing, matching old implementation
                writeJob?.cancelAndJoin()
                logger.debug { "Transport coroutine completed, calling onClose" }
                callOnCloseOnce()
            }
        }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        check(initialized.load()) { "Transport is not started" }
        check(!onCloseCalled.load()) { "Transport is closed" }
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        try {
            sendChannel.send(message)
        } catch (e: ClosedSendChannelException) {
            logger.debug(e) { "Cannot send message: transport is closed" }
            throw McpException(CONNECTION_CLOSED, "Transport is closed")
        } catch (e: McpException) {
            logger.debug(e) { "Error while sending message: ${e.message}" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error while sending message: ${e.message}" }
            throw McpException(INTERNAL_ERROR, "Error while sending message: ${e.message}")
        }
    }

    override suspend fun close() {
        if (!initialized.compareAndSet(expectedValue = true, newValue = false)) {
            return // Already closed
        }
        scope.stopProcessing("Closed")
        scope.coroutineContext[Job]?.join() // Wait for all coroutines to complete
    }

    private fun callOnCloseOnce() {
        if (onCloseCalled.compareAndSet(expectedValue = false, newValue = true)) {
            runCatching { _onClose() }
        }
    }

    private fun sendOutboundMessage(message: JSONRPCMessage, sink: Sink, mainScope: CoroutineScope) {
        try {
            val json = serializeMessage(message)
            sink.writeString(json)
            sink.flush()
        } catch (e: SerializationException) {
            logger.warn(e) { "Can't serialize message" }
            runCatching { _onError(McpException(INTERNAL_ERROR, "Serialization error")) }
            mainScope.stopProcessing("Can't serialize message", e)
        } catch (e: IOException) {
            logger.warn(e) { "Can't send message" }
            runCatching { _onError(McpException(CONNECTION_CLOSED, "Can't send message. Connection closed")) }
            mainScope.stopProcessing("Write I/O failed", e)
        }
    }

    private suspend fun handleJSONRPCMessage(msg: JSONRPCMessage) {
        @Suppress("TooGenericExceptionCaught")
        try {
            _onMessage.invoke(msg)
        } catch (e: Throwable) {
            logger.error(e) { "Error processing message." }
            runCatching { _onError.invoke(e) }
        }
    }

    private fun CoroutineScope.stopProcessing(reason: String, cause: Throwable? = null) {
        sendChannel.close() // Stop accepting new messages
        callOnCloseOnce()
        cancel(reason, cause) // cancel current coroutine context
    }

    private suspend fun CoroutineScope.readSource(
        stream: ProcessStream,
        source: Source,
        channel: ProducerScope<Event>,
        bytesConsumer: suspend (ByteArray) -> Unit,
    ) {
        val buffer = Buffer()
        try {
            source.use { source ->
                while (isActive) {
                    val bytesRead = source.readAtMostTo(buffer, BUFFER_SIZE)
                    if (bytesRead == -1L) {
                        logger.debug { "EOF reached in $stream" }
                        channel.send(Event.EOFEvent(stream))
                        break
                    }

                    if (bytesRead > 0L) {
                        val bytes = buffer.readByteArray()
                        buffer.clear()
                        bytesConsumer.invoke(bytes)
                    }

                    yield() // Giving other coroutines a chance to run
                }
            }
        } catch (exception: IOException) {
            logger.debug(exception) { "IOException while reading stream" }
            channel.send(Event.IOErrorEvent(stream, exception))
        } finally {
            buffer.clear()
        }
    }

    private enum class ProcessStream { Stdin, Stderr, Stdout }

    /**
     * Represents an event in the communication process.
     *
     * Events are a sealed hierarchy of different types of communication signals
     * between processes. These events are used to manage and interpret information
     * or errors generated during the operation of the associated transport.
     */
    private sealed interface Event {

        data class JsonRpc(val message: JSONRPCMessage) : Event

        data class StderrEvent(val message: String) : Event

        data class EOFEvent(val stream: ProcessStream) : Event

        data class IOErrorEvent(val stream: ProcessStream, val cause: Throwable) : Event {
            override fun toString(): String = "IOErrorEvent(stream=$stream, cause=${cause.message})"
        }
    }
}
