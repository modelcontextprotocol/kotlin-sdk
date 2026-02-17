package io.modelcontextprotocol.kotlin.test.utils

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 *  A transport implementation that uses Kotlin Coroutines Channels for asynchronous
 * communication of JSON-RPC messages. This is useful in scenarios where communication is
 * message-based and channels are used as the underlying mechanism for delivery.
 *
 * @constructor Initializes the transport with the specified [kotlinx.coroutines.channels.SendChannel] and [kotlinx.coroutines.channels.ReceiveChannel].
 * @property sendChannel The channel used to send JSON-RPC messages.
 * @property receiveChannel The channel used to receive JSON-RPC messages.
 * @param dispatcher The coroutine dispatcher for the event loop. Defaults to [kotlinx.coroutines.Dispatchers.Default].
 */
@ExperimentalMcpApi
public class ChannelTransport(
    private val sendChannel: SendChannel<JSONRPCMessage>,
    private val receiveChannel: ReceiveChannel<JSONRPCMessage>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AbstractClientTransport() {

    override val logger: KLogger = KotlinLogging.logger {}

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /**
     * Creates a `ChannelTransport` instance using a single channel for both sending and receiving messages.
     *
     * This constructor simplifies the use of `ChannelTransport` in scenarios where a single channel
     * is enough for bidirectional communication. A default channel with unlimited capacity is used
     * if no channel is provided.
     *
     * @param channel The `Channel` of `JSONRPCMessage` instances, used for both sending and receiving.
     * Defaults to a channel with unlimited capacity.
     * @param dispatcher The coroutine dispatcher for the event loop. Defaults to [Dispatchers.Default].
     */
    public constructor(
        channel: Channel<JSONRPCMessage> = Channel(Channel.UNLIMITED),
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : this(channel, channel, dispatcher)

    /**
     * Represents a pair of interconnected [ChannelTransport]s for bidirectional communication.
     *
     * Messages sent via one transport are received by the other. This is useful for
     * setting up communication between a client and server without requiring external networking.
     *
     * @property clientTransport The transport intended for use on the client-side.
     * @property serverTransport The transport intended for use on the server-side.
     */
    public data class LinkedTransports(val clientTransport: ChannelTransport, val serverTransport: ChannelTransport)

    public companion object {

        /**
         * Creates a pair of interconnected [ChannelTransport] objects for bidirectional communication.
         *
         * This method sets up a client and server transport using Kotlin channels, enabling messages sent
         * through one transport to be received by the other. It is typically used for testing or in-memory
         * communication without external networking.
         *
         * @param capacity The buffer capacity of the internal channels used for communication. Defaults to 256.
         * @return A [LinkedTransports] instance containing the client and server transports.
         */
        public fun createLinkedPair(
            capacity: Int = 256,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): LinkedTransports {
            val sendChannel = Channel<JSONRPCMessage>(capacity)
            val receiveChannel = Channel<JSONRPCMessage>(capacity)
            val clientTransport = ChannelTransport(sendChannel, receiveChannel, dispatcher)
            val serverTransport = ChannelTransport(receiveChannel, sendChannel, dispatcher)
            return LinkedTransports(clientTransport = clientTransport, serverTransport = serverTransport)
        }
    }

    /**
     * Starts the transport, allowing it to begin processing messages.
     *
     * Waits for the event loop to start before returning, ensuring the transport is
     * fully initialized and ready to send/receive messages.
     *
     * Processes messages until the channel closes or cancellation occurs.
     * Exceptions from message handlers are reported via onError but do not stop processing.
     */
    override suspend fun initialize() {
        logger.info { "ChannelTransport starting message processing" }
        val started = CompletableDeferred<Unit>()
        @Suppress("TooGenericExceptionCaught")
        scope.launch(CoroutineName("ChannelTransport#${hashCode()}-event-loop")) {
            try {
                // Signal that event loop has started, then yield to ensure we're ready
                started.complete(Unit)
                yield()

                for (message in receiveChannel) {
                    logger.debug { "Received message: ${message::class.simpleName}" }

                    try {
                        _onMessage.invoke(message)
                        logger.trace { "Message processed successfully: ${message::class.simpleName}" }
                    } catch (e: CancellationException) {
                        // Let cancellation propagate immediately
                        logger.debug { "Cancellation requested during message processing" }
                        throw e
                    } catch (e: Exception) {
                        // Report other errors but continue processing
                        logger.warn(e) { "Error processing message: ${message::class.simpleName}" }
                        _onError.invoke(e)
                    }
                }
                logger.info { "ChannelTransport stopped: receive channel closed" }
            } catch (e: Exception) {
                // Only complete exceptionally if not already completed
                if (!started.isCompleted) {
                    started.completeExceptionally(e)
                }
                throw e
            } finally {
                close()
            }
        }
        // Wait for the event loop to start
        started.await()
    }

    /**
     * Sends a JSON-RPC message through the transport.
     *
     * @param message The JSON-RPC message to send. This can be a request or a response,
     *                conforming to the JSON-RPC 2.0 specification.
     * @param options Optional transport-specific options that provide additional context or behavior
     *                for the message transmission, such as associating the message with a specific
     *                incoming request or handling resumption tokens.
     */
    override suspend fun performSend(message: JSONRPCMessage, options: TransportSendOptions?) {
        logger.debug { "Sending message: ${message::class.simpleName}" }
        sendChannel.send(message)
        logger.trace { "Message sent successfully: ${message::class.simpleName}" }
    }

    /**
     * Closes the transport, preventing further message transmission and processing.
     */
    override suspend fun closeResources() {
        logger.info { "Closing ChannelTransport" }
        sendChannel.close()
        if (receiveChannel !== sendChannel) {
            logger.debug { "Cancelling separate receive channel" }
            receiveChannel.cancel()
        }
        scope.cancel()
        scope.coroutineContext[Job.Key]?.join() // Wait for cleanup
        logger.info { "ChannelTransport closed" }
    }
}
