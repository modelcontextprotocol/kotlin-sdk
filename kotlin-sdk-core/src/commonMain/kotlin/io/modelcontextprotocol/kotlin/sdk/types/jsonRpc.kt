@file:OptIn(ExperimentalAtomicApi::class, ExperimentalSerializationApi::class)

package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.jvm.JvmInline

public const val JSONRPC_VERSION: String = "2.0"

private val REQUEST_MESSAGE_ID: AtomicLong = AtomicLong(0L)

public fun RequestId(value: String): RequestId = RequestId.StringId(value)

public fun RequestId(value: Long): RequestId = RequestId.NumberId(value)

/**
 * A uniquely identifying ID for a request in JSON-RPC.
 */
@Serializable(with = RequestIdPolymorphicSerializer::class)
public sealed interface RequestId {

    @JvmInline
    @Serializable
    public value class StringId(public val value: String) : RequestId

    @JvmInline
    @Serializable
    public value class NumberId(public val value: Long) : RequestId
}

/**
 * Converts the request to a JSON-RPC request.
 *
 * @return The JSON-RPC request representation.
 */
public fun Request.toJSON(): JSONRPCRequest = JSONRPCRequest(
    method = method.value,
    params = this.params?.let {
        McpJson.encodeToJsonElement(it)
    },
)

/**
 * Decodes a JSON-RPC request into a protocol-specific [Request].
 *
 * @return The decoded [Request]
 */
public fun JSONRPCRequest.fromJSON(): Request =
    McpJson.decodeFromJsonElement<Request>(McpJson.encodeToJsonElement(this))

/**
 * Converts the notification to a JSON-RPC notification.
 *
 * @return The JSON-RPC notification representation.
 */
public fun Notification.toJSON(): JSONRPCNotification = JSONRPCNotification(
    method = method.value,
    params = this.params?.let {
        McpJson.encodeToJsonElement(it)
    },
)

/**
 * Decodes a JSON-RPC notification into a protocol-specific [Notification].
 *
 * @return The decoded [Notification].
 */
internal fun JSONRPCNotification.fromJSON(): Notification =
    McpJson.decodeFromJsonElement<Notification>(McpJson.encodeToJsonElement(this))

/**
 * Base interface for all JSON-RPC 2.0 messages.
 *
 * All messages in the MCP protocol follow the JSON-RPC 2.0 specification.
 */
@Serializable(with = JSONRPCMessagePolymorphicSerializer::class)
public sealed interface JSONRPCMessage {
    public val jsonrpc: String
}

// ============================================================================
// JSONRPCRequest
// ============================================================================

/**
 * A request that expects a response.
 *
 * Requests are identified by a unique [id] and specify a [method] to invoke.
 * The server or client (depending on direction) must respond with either a
 * [JSONRPCResponse] or [JSONRPCError] that has the same [id].
 *
 * @property jsonrpc Always "2.0" to indicate JSON-RPC 2.0 protocol.
 * @property id A unique identifier for this request. The response will include the same ID.
 * Can be a string or number.
 * @property method The name of the method to invoke (e.g., "tools/list", "resources/read").
 * @property params Optional parameters for the method. Structure depends on the specific method.
 */
@Serializable
public data class JSONRPCRequest(
    val id: RequestId = RequestId(REQUEST_MESSAGE_ID.incrementAndFetch()),
    val method: String,
    val params: JsonElement? = null,
) : JSONRPCMessage {
    @EncodeDefault
    override val jsonrpc: String = JSONRPC_VERSION
}

// ============================================================================
// JSONRPCNotification
// ============================================================================

/**
 * A notification which does not expect a response.
 *
 * Notifications are fire-and-forget messages. They do not have an `id` and
 * the recipient does not send any response (neither success nor error).
 *
 * Examples: progress updates, resource change notifications, log messages.
 *
 * @property jsonrpc Always "2.0" to indicate JSON-RPC 2.0 protocol.
 * @property method The name of the notification method (e.g., "notifications/progress",
 * "notifications/resources/updated").
 * @property params Optional parameters for the notification. Structure depends on the specific method.
 */
@Serializable
public data class JSONRPCNotification(val method: String, val params: JsonElement? = null) : JSONRPCMessage {
    @EncodeDefault
    override val jsonrpc: String = JSONRPC_VERSION
}

// ============================================================================
// JSONRPCResponse
// ============================================================================

/**
 * A successful (non-error) response to a request.
 *
 * Sent in response to a [JSONRPCRequest] when the method execution succeeds.
 * The [id] must match the [id] of the original request.
 *
 * @property jsonrpc Always "2.0" to indicate JSON-RPC 2.0 protocol.
 * @property id The identifier from the original request. Used to match responses to requests.
 * @property result The result of the method execution. Structure depends on the method that was called.
 */
@Serializable
public data class JSONRPCResponse(val id: RequestId, val result: RequestResult = EmptyResult()) : JSONRPCMessage {
    @EncodeDefault
    override val jsonrpc: String = JSONRPC_VERSION
}

// ============================================================================
// JSONRPCError
// ============================================================================

/**
 * A response to a request that indicates an error occurred.
 *
 * Sent in response to a [JSONRPCRequest] when the method execution fails.
 * The [id] must match the [id] of the original request.
 *
 * @property jsonrpc Always "2.0" to indicate JSON-RPC 2.0 protocol.
 * @property id The identifier from the original request. Used to match error responses to requests.
 * @property error Details about the error that occurred, including error code and message.
 */
@Serializable
public data class JSONRPCError(val id: RequestId, val error: RPCError) : JSONRPCMessage {
    @EncodeDefault
    override val jsonrpc: String = JSONRPC_VERSION
}

/**
 * Error information for a failed JSON-RPC request.
 *
 * @property code The error type that occurred. A number indicating the error category.
 * See standard JSON-RPC 2.0 error codes:
 * - -32700: Parse error (invalid JSON)
 * - -32600: Invalid request (not valid JSON-RPC)
 * - -32601: Method not found
 * - -32602: Invalid params
 * - -32603: Internal error
 * - -32000 to -32099: Server-defined errors
 * @property message A short description of the error.
 * The message SHOULD be limited to a concise single sentence.
 * @property data Additional information about the error.
 * The value of this member is defined by the sender
 * (e.g., detailed error information, nested errors, etc.).
 */
@Serializable
public data class RPCError(val code: Int, val message: String, val data: JsonElement? = null) {
    /**
     * Standard JSON-RPC 2.0 and MCP SDK error codes.
     */
    public object ErrorCode {
        // SDK-specific error codes
        /** Connection was closed */
        public const val CONNECTION_CLOSED: Int = -32000

        /** Request timed out */
        public const val REQUEST_TIMEOUT: Int = -32001

        // Standard JSON-RPC 2.0 error codes
        /** Invalid JSON was received */
        public const val PARSE_ERROR: Int = -32700

        /** The JSON sent is not a valid Request object */
        public const val INVALID_REQUEST: Int = -32600

        /** The method does not exist or is not available */
        public const val METHOD_NOT_FOUND: Int = -32601

        /** Invalid method parameter(s) */
        public const val INVALID_PARAMS: Int = -32602

        /** Internal JSON-RPC error */
        public const val INTERNAL_ERROR: Int = -32603
    }
}
