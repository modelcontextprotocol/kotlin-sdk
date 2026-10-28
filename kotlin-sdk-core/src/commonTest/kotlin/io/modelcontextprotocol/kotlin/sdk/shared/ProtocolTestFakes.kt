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
import kotlinx.serialization.json.decodeFromJsonElement
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
    override fun assertRequestHandlerCapability(method: Method) {
        // noop
    }
}

/** Shared [Transport] test double: records every sent message (with its options) and replays inbound ones. */
internal class RecordingTransport(
    private val startFailure: Throwable? = null,
    private val sendFailure: Throwable? = null,
) : Transport {
    private val sentMessages = Channel<JSONRPCMessage>(Channel.UNLIMITED)
    private var onMessageCallback: (suspend (JSONRPCMessage) -> Unit)? = null

    val sentWithOptions = mutableListOf<Pair<JSONRPCMessage, TransportSendOptions?>>()

    var closeCallback: (() -> Unit)? = null
        private set

    override suspend fun start() {
        startFailure?.let { throw it }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        sendFailure?.let { throw it }
        sentWithOptions.add(message to options)
        sentMessages.send(message)
    }

    override suspend fun close() {
        closeCallback?.invoke()
    }

    override fun onClose(block: () -> Unit) {
        closeCallback = block
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

    suspend fun deliver(message: JSONRPCMessage) {
        val callback = onMessageCallback ?: error("onMessage callback not registered")
        callback(message)
    }
}

internal suspend fun connectedProtocol(options: ProtocolOptions? = null): Pair<TestProtocol, RecordingTransport> {
    val protocol = TestProtocol(options)
    val transport = RecordingTransport()
    protocol.connect(transport)
    return protocol to transport
}

internal fun responsesOn(transport: RecordingTransport): List<JSONRPCResponse> =
    transport.sentWithOptions.map { it.first }.filterIsInstance<JSONRPCResponse>()

internal fun errorsOn(transport: RecordingTransport): List<JSONRPCError> =
    transport.sentWithOptions.map { it.first }.filterIsInstance<JSONRPCError>()

internal fun cancellationsOn(transport: RecordingTransport): List<CancelledNotificationParams> =
    transport.sentWithOptions.map { it.first }
        .filterIsInstance<JSONRPCNotification>()
        .filter { it.method == Method.Defined.NotificationsCancelled.value }
        .map { McpJson.decodeFromJsonElement<CancelledNotificationParams>(it.params!!) }

internal fun cancelledNotification(requestId: RequestId, reason: String): JSONRPCNotification = JSONRPCNotification(
    method = Method.Defined.NotificationsCancelled.value,
    params = McpJson.encodeToJsonElement(CancelledNotificationParams(requestId = requestId, reason = reason)),
)
