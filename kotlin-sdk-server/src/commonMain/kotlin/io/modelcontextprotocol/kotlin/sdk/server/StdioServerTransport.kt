package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.internal.IODispatcher
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.ReadBuffer
import io.modelcontextprotocol.kotlin.sdk.shared.TooLongFrameException
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.shared.serializeMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpDsl
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val READ_BUFFER_SIZE = 8192L

/**
 * Server-side MCP transport that exchanges JSON-RPC messages over arbitrary byte streams.
 *
 * Reads framed messages from the supplied [Source] and writes framed messages to the supplied
 * [Sink]. Three internal coroutines drive the pipeline:
 *
 * - **reader** — pulls bytes from the input source into the parsing buffer; runs on
 *   [Builder.ioDispatcher].
 * - **processor** — parses messages out of the buffer and invokes the registered message handler;
 *   runs on [Builder.handlerDispatcher] (defaults to [Dispatchers.Default]) so blocking handler
 *   code does not starve the I/O pool.
 * - **writer** — serialises outbound messages and flushes them to the output sink; runs on
 *   [Builder.ioDispatcher].
 *
 * Both internal channels are bounded, so a slow handler or slow output naturally back-pressures
 * the upstream producer — [send] suspends when the outbound channel is full.
 *
 * Both explicit [close] and a natural EOF from the input perform a graceful drain: in-flight
 * outbound messages are flushed before `onClose` fires, and the input source and output sink are
 * released.
 *
 * Example:
 * ```kotlin
 * val transport = StdioServerTransport(
 *     input = System.`in`.asSource().buffered(),
 *     output = System.out.asSink().buffered(),
 * ) {
 *     scope = myScope
 * }
 * transport.start()
 * ```
 */
@OptIn(ExperimentalAtomicApi::class)
public class StdioServerTransport private constructor(
    private val input: Source,
    output: Sink,
    private val scope: CoroutineScope?,
    private val handlerDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
) : AbstractTransport() {

    /**
     * Creates a [StdioServerTransport] reading from [input] and writing to [output], with
     * optional configuration applied through a [Builder] block.
     *
     * @param input source the transport reads JSON-RPC messages from
     * @param output sink the transport writes JSON-RPC messages to
     * @param block configuration applied to the underlying [Builder]
     */
    public constructor(
        input: Source,
        output: Sink,
        block: Builder.() -> Unit = {},
    ) : this(Builder(input, output).apply(block))

    private constructor(builder: Builder) : this(
        input = builder.input,
        output = builder.output,
        scope = builder.scope,
        handlerDispatcher = builder.handlerDispatcher,
        ioDispatcher = builder.ioDispatcher,
    )

    /**
     * Creates a [StdioServerTransport] from the given input source and output sink with default
     * configuration. Retained for binary compatibility; prefer the [Builder]-based constructor.
     *
     * @param inputStream source the transport reads JSON-RPC messages from
     * @param outputStream sink the transport writes JSON-RPC messages to
     */
    @Deprecated(
        message = "Use StdioServerTransport(input, output) { ... } instead.",
        replaceWith = ReplaceWith("StdioServerTransport(input = inputStream, output = outputStream)"),
        level = DeprecationLevel.WARNING,
    )
    public constructor(inputStream: Source, outputStream: Sink) : this(
        input = inputStream,
        output = outputStream,
        scope = null,
        handlerDispatcher = Dispatchers.Default,
        ioDispatcher = IODispatcher.limitedParallelism(2),
    )

    private val logger = KotlinLogging.logger {}
    private val readBuffer = ReadBuffer()
    private val readChannel = Channel<ByteArray>(Channel.BUFFERED)
    private val writeChannel = Channel<JSONRPCMessage>(Channel.BUFFERED)
    private val outputSink = output.buffered()

    private enum class State { New, Operational, Stopped }

    private val state: AtomicReference<State> = AtomicReference(State.New)

    private var readerJob: Job? = null
    private var processorJob: Job? = null
    private var writerJob: Job? = null

    private var effectiveScope: CoroutineScope? = null
    private val ownsScope: Boolean = (scope == null)

    private val setupComplete = CompletableDeferred<Unit>()

    /**
     * Starts the reader, processor, and writer coroutines. Must be called exactly once before
     * messages can be exchanged; subsequent calls throw.
     */
    override suspend fun start() {
        if (!state.compareAndSet(State.New, State.Operational)) {
            when (state.load()) {
                State.Stopped -> error("StdioServerTransport is already closed!")
                else -> error("StdioServerTransport already started!")
            }
        }

        try {
            val resolvedScope = scope ?: CoroutineScope(
                currentCoroutineContext() + IODispatcher + SupervisorJob(),
            )
            effectiveScope = resolvedScope

            readerJob = resolvedScope.launch(
                ioDispatcher + CoroutineName("StdioServerTransport.reader"),
            ) { readerPump() }
            processorJob = resolvedScope.launch(
                handlerDispatcher + CoroutineName("StdioServerTransport.processor"),
                start = CoroutineStart.UNDISPATCHED,
            ) { processorPump() }
            writerJob = resolvedScope.launch(
                ioDispatcher + CoroutineName("StdioServerTransport.writer"),
                start = CoroutineStart.UNDISPATCHED,
            ) { writerPump() }
        } finally {
            setupComplete.complete(Unit)
        }
    }

    private suspend fun readerPump() {
        val buf = Buffer()
        try {
            while (currentCoroutineContext().isActive) {
                val bytesRead = input.readAtMostTo(buf, READ_BUFFER_SIZE)
                if (bytesRead == -1L) break
                if (bytesRead > 0) {
                    val chunk = buf.readByteArray()
                    readChannel.send(chunk)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (state.load() == State.Stopped) {
                logger.debug(e) { "Reader interrupted by close()" }
            } else {
                logger.error(e) { "Error reading from input source" }
                _onError(e)
            }
        } finally {
            readChannel.close()
        }
    }

    private suspend fun processorPump() {
        try {
            for (chunk in readChannel) {
                readBuffer.append(chunk)
                while (true) {
                    val message = try {
                        readBuffer.readMessage()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: TooLongFrameException) {
                        throw e
                    } catch (e: Throwable) {
                        _onError(e)
                        null
                    } ?: break
                    try {
                        _onMessage(message)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        logger.error(e) { "Error processing message" }
                        _onError(e)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _onError(e)
        } finally {
            writeChannel.close()
        }
    }

    private suspend fun writerPump() {
        try {
            for (message in writeChannel) {
                val json = serializeMessage(message)
                outputSink.writeString(json)
                outputSink.flush()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Error writing to output sink" }
            _onError(e)
        } finally {
            transitionToStoppedNaturally()
        }
    }

    /**
     * Closes the transport. When called after [start], waits for in-flight outbound messages to be
     * flushed, releases the input source and output sink, cancels the internal scope when the
     * transport owns it, and invokes `onClose`. When called before [start], transitions directly
     * to the closed state without releasing the input/output or invoking `onClose` — the caller
     * remains responsible for resources that were never handed off to a running transport. Safe to
     * call multiple times and safe to race with [start].
     */
    override suspend fun close() {
        var previous: State
        do {
            previous = state.load()
        } while (previous != State.Stopped && !state.compareAndSet(previous, State.Stopped))

        if (previous == State.New) {
            setupComplete.complete(Unit)
            return
        }

        withContext(NonCancellable) {
            setupComplete.await()

            if (previous == State.Stopped) {
                writerJob?.join()
                return@withContext
            }

            runCatching { input.close() }
                .onFailure { logger.warn(it) { "Failed to close input source" } }
            readerJob?.cancel()
            readChannel.close()
            processorJob?.cancelAndJoin()
            writeChannel.close()
            writerJob?.join()
            runCatching { outputSink.close() }
                .onFailure { logger.warn(it) { "Failed to close output sink" } }
            readBuffer.clear()
            if (ownsScope) {
                effectiveScope?.coroutineContext?.get(Job)?.cancel()
            }
            invokeOnCloseCallback()
        }
    }

    /**
     * Queues [message] for the writer coroutine. Suspends when the outbound channel is full,
     * applying back-pressure to the caller. Throws [McpException] with
     * [RPCError.ErrorCode.CONNECTION_CLOSED] if the transport has not been started or has
     * already closed.
     *
     * @param message JSON-RPC message to send
     * @param options transport-specific send options; ignored by this transport
     */
    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        when (state.load()) {
            State.New -> throw McpException(
                code = RPCError.ErrorCode.CONNECTION_CLOSED,
                message = "Transport is not started",
            )

            State.Stopped -> throw McpException(
                code = RPCError.ErrorCode.CONNECTION_CLOSED,
                message = "Transport is closed",
            )

            State.Operational -> Unit
        }
        try {
            writeChannel.send(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClosedSendChannelException) {
            throw McpException(
                code = RPCError.ErrorCode.CONNECTION_CLOSED,
                message = "Transport is closed",
                cause = e,
            )
        }
    }

    private fun transitionToStoppedNaturally() {
        if (!state.compareAndSet(State.Operational, State.Stopped)) return
        runCatching { input.close() }
            .onFailure { logger.warn(it) { "Failed to close input source" } }
        runCatching { outputSink.close() }
            .onFailure { logger.warn(it) { "Failed to close output sink" } }
        readBuffer.clear()
        if (ownsScope) {
            // Non-null in practice: writer launches after effectiveScope is published.
            effectiveScope?.coroutineContext?.get(Job)?.cancel()
        }
        invokeOnCloseCallback()
    }

    /**
     * Configuration builder for [StdioServerTransport]. Used via the
     * `StdioServerTransport(input, output) { ... }` factory; the I/O endpoints are supplied
     * positionally, while [scope], [handlerDispatcher], and [ioDispatcher] are configurable
     * inside the block.
     *
     * Example:
     * ```kotlin
     * StdioServerTransport(input, output) {
     *     scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
     *     handlerDispatcher = myHandlerDispatcher
     *     ioDispatcher = Dispatchers.IO
     * }
     * ```
     *
     * @property scope Optional caller-supplied [CoroutineScope]. When non-null, the transport's
     *   pipeline coroutines run as children of this scope and the transport does not tear it
     *   down on [close]. When `null`, the transport creates and owns an internal scope.
     * @property handlerDispatcher Dispatcher for invoking the registered message handler.
     *   Defaults to [Dispatchers.Default].
     * @property ioDispatcher Dispatcher for the reader and writer coroutines. Must allow at
     *   least two threads to run concurrently so the reader and writer don't block each other.
     *   The default is a two-thread view of the platform I/O dispatcher
     *   (`IODispatcher.limitedParallelism(2)`); pass a different value to share or isolate I/O
     *   threads with the rest of your application.
     */
    @McpDsl
    public class Builder internal constructor(internal val input: Source, internal val output: Sink) {
        public var scope: CoroutineScope? = null
        public var handlerDispatcher: CoroutineDispatcher = Dispatchers.Default
        public var ioDispatcher: CoroutineDispatcher = IODispatcher.limitedParallelism(2)
    }
}
