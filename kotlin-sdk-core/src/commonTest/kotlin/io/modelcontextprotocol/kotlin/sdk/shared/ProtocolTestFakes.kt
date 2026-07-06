package io.modelcontextprotocol.kotlin.sdk.shared

import io.modelcontextprotocol.kotlin.sdk.types.CancelledNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Shared [Protocol] test double: records [onError] calls, counts `notifications/initialized`
 * router-hook invocations, and no-ops all capability assertions.
 *
 * The concurrency gate is flipped only via [enableConcurrency]; nothing in this double flips it
 * implicitly, so tests keep today's serial semantics unless they opt in.
 */
internal class TestProtocol(options: ProtocolOptions? = null) : Protocol(options) {
    val errors = mutableListOf<Throwable>()
    var initializedNotificationCount = 0

    fun enableConcurrency() {
        enableConcurrentDispatch()
    }

    override fun onInitializedNotification() {
        initializedNotificationCount++
        // deliberately does NOT flip the gate — tests control the flip explicitly
    }

    override fun onError(error: Throwable) {
        errors.add(error)
    }

    override fun assertCapabilityForMethod(method: Method) {
        // noop
    }
    override fun assertNotificationCapability(method: Method) {
        // noop
    }
    public override fun assertRequestHandlerCapability(method: Method) {
        // noop
    }
}

/** Shared [Transport] test double: records every sent message (with its options) and replays inbound ones. */
internal class RecordingTransport : Transport {
    val sentMessages = Channel<JSONRPCMessage>(Channel.UNLIMITED)
    private var onMessageCallback: (suspend (JSONRPCMessage) -> Unit)? = null
    private var onCloseCallback: (() -> Unit)? = null

    val sentWithOptions = mutableListOf<Pair<JSONRPCMessage, TransportSendOptions?>>()

    var closeCallback: (() -> Unit)? = null
        private set

    override suspend fun start() {
        // noop
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        sentWithOptions.add(message to options)
        sentMessages.send(message)
    }

    override suspend fun close() {
        onCloseCallback?.invoke()
    }

    override fun onClose(block: () -> Unit) {
        closeCallback = block
        onCloseCallback = block
    }

    override fun onError(block: (Throwable) -> Unit) {
        // noop
    }

    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {
        onMessageCallback = block
    }

    suspend fun awaitRequest(): JSONRPCRequest {
        while (true) {
            val message = sentMessages.receive()
            if (message is JSONRPCRequest) return message
        }
    }

    /** Cross-thread-safe reader used by real-dispatcher tests; [sentWithOptions] is single-thread only. */
    suspend fun awaitSent(): JSONRPCMessage = sentMessages.receive()

    suspend fun deliver(message: JSONRPCMessage) {
        val callback = onMessageCallback ?: error("onMessage callback not registered")
        callback(message)
    }
}

internal fun responsesOn(transport: RecordingTransport): List<JSONRPCResponse> =
    transport.sentWithOptions.map { it.first }.filterIsInstance<JSONRPCResponse>()

internal fun errorsOn(transport: RecordingTransport): List<JSONRPCError> =
    transport.sentWithOptions.map { it.first }.filterIsInstance<JSONRPCError>()

internal fun cancelledNotification(requestId: RequestId, reason: String): JSONRPCNotification = JSONRPCNotification(
    method = Method.Defined.NotificationsCancelled.value,
    params = McpJson.encodeToJsonElement(CancelledNotificationParams(requestId = requestId, reason = reason)),
)
