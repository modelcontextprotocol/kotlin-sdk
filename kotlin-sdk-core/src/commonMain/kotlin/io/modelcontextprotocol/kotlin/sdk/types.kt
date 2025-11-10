@file:Suppress("unused", "EnumEntryName")

package io.modelcontextprotocol.kotlin.sdk

import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.jvm.JvmInline

@Deprecated(
    message = "Use `LATEST_PROTOCOL_VERSION` instead",
    replaceWith = ReplaceWith(
        "LATEST_PROTOCOL_VERSION",
        "io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION",
    ),
    level = DeprecationLevel.WARNING,
)
public const val LATEST_PROTOCOL_VERSION: String = "2025-03-26"

@Deprecated(
    message = "Use `SUPPORTED_PROTOCOL_VERSIONS` instead",
    replaceWith = ReplaceWith(
        "SUPPORTED_PROTOCOL_VERSIONS",
        "io.modelcontextprotocol.kotlin.sdk.types.SUPPORTED_PROTOCOL_VERSIONS",
    ),
    level = DeprecationLevel.WARNING,
)
public val SUPPORTED_PROTOCOL_VERSIONS: Array<String> = arrayOf(
    LATEST_PROTOCOL_VERSION,
    "2024-11-05",
)

@Deprecated(
    message = "Use `JSONRPC_VERSION` instead",
    replaceWith = ReplaceWith("JSONRPC_VERSION", "io.modelcontextprotocol.kotlin.sdk.types.JSONRPC_VERSION"),
    level = DeprecationLevel.WARNING,
)
public const val JSONRPC_VERSION: String = "2.0"

@OptIn(ExperimentalAtomicApi::class)
@Deprecated(
    message = "Use `REQUEST_MESSAGE_ID` instead",
    replaceWith = ReplaceWith("REQUEST_MESSAGE_ID", "io.modelcontextprotocol.kotlin.sdk.types.REQUEST_MESSAGE_ID"),
    level = DeprecationLevel.WARNING,
)
private val REQUEST_MESSAGE_ID: AtomicLong = AtomicLong(0L)

/**
 * A progress token, used to associate progress notifications with the original request.
 * Stores message ID.
 */
@Deprecated(
    message = "Use `ProgressToken` instead",
    replaceWith = ReplaceWith("ProgressToken", "io.modelcontextprotocol.kotlin.sdk.types.ProgressToken"),
    level = DeprecationLevel.WARNING,
)
public typealias ProgressToken = RequestId

/**
 * An opaque token used to represent a cursor for pagination.
 */
@Deprecated(
    message = "This alias will be removed. Use String directly instead.",
    replaceWith = ReplaceWith("String"),
    level = DeprecationLevel.WARNING,
)
public typealias Cursor = String

/**
 * Represents an entity that includes additional metadata in its responses.
 */
@Serializable
@Deprecated(
    message = "Use `WithMeta` instead",
    replaceWith = ReplaceWith("WithMeta", "io.modelcontextprotocol.kotlin.sdk.types.WithMeta"),
    level = DeprecationLevel.WARNING,
)
public sealed interface WithMeta {
    /**
     * The protocol reserves this result property
     * to allow clients and servers to attach additional metadata to their responses.
     */
    @Suppress("PropertyName")
    public val _meta: JsonObject

    public companion object {
        public val Empty: CustomMeta = CustomMeta()
    }
}

/**
 * An implementation of [WithMeta] containing custom metadata.
 *
 * @param _meta The JSON object holding metadata. Defaults to an empty JSON object.
 */
@Serializable
@Deprecated(
    message = "This class will be removed. Use `WithMeta` instead",
    replaceWith = ReplaceWith("WithMeta", "io.modelcontextprotocol.kotlin.sdk.types.WithMeta"),
    level = DeprecationLevel.WARNING,
)
public class CustomMeta(override val _meta: JsonObject = EmptyJsonObject) : WithMeta

/**
 * Represents a method in the protocol, which can be predefined or custom.
 */
@Serializable(with = RequestMethodSerializer::class)
@Deprecated(
    message = "Use `Method` instead",
    replaceWith = ReplaceWith("Method", "io.modelcontextprotocol.kotlin.sdk.types.Method"),
    level = DeprecationLevel.WARNING,
)
public sealed interface Method {
    public val value: String

    /**
     * Enum of predefined methods supported by the protocol.
     */
    @Serializable
    public enum class Defined(override val value: String) : Method {
        Initialize("initialize"),
        Ping("ping"),
        ResourcesList("resources/list"),
        ResourcesTemplatesList("resources/templates/list"),
        ResourcesRead("resources/read"),
        ResourcesSubscribe("resources/subscribe"),
        ResourcesUnsubscribe("resources/unsubscribe"),
        PromptsList("prompts/list"),
        PromptsGet("prompts/get"),
        NotificationsCancelled("notifications/cancelled"),
        NotificationsInitialized("notifications/initialized"),
        NotificationsProgress("notifications/progress"),
        NotificationsMessage("notifications/message"),
        NotificationsResourcesUpdated("notifications/resources/updated"),
        NotificationsResourcesListChanged("notifications/resources/list_changed"),
        NotificationsToolsListChanged("notifications/tools/list_changed"),
        NotificationsRootsListChanged("notifications/roots/list_changed"),
        NotificationsPromptsListChanged("notifications/prompts/list_changed"),
        ToolsList("tools/list"),
        ToolsCall("tools/call"),
        LoggingSetLevel("logging/setLevel"),
        SamplingCreateMessage("sampling/createMessage"),
        CompletionComplete("completion/complete"),
        RootsList("roots/list"),
        ElicitationCreate("elicitation/create"),
    }

    /**
     * Represents a custom method defined by the user.
     */
    @Serializable
    public data class Custom(override val value: String) : Method
}

/**
 * Represents a request in the protocol.
 */
@Serializable(with = RequestPolymorphicSerializer::class)
@Deprecated(
    message = "Use `Request` instead",
    replaceWith = ReplaceWith("Request", "io.modelcontextprotocol.kotlin.sdk.types.Request"),
    level = DeprecationLevel.WARNING,
)
public sealed interface Request {
    public val method: Method
}

/**
 * Converts the request to a JSON-RPC request.
 *
 * @return The JSON-RPC request representation.
 */
@Deprecated(
    message = "Use `toJSON` instead",
    replaceWith = ReplaceWith("toJSON", "io.modelcontextprotocol.kotlin.sdk.types.toJSON"),
    level = DeprecationLevel.WARNING,
)
public fun Request.toJSON(): JSONRPCRequest {
    val fullJson = McpJson.encodeToJsonElement(this).jsonObject
    val params = JsonObject(fullJson.filterKeys { it != "method" })
    return JSONRPCRequest(
        method = method.value,
        params = params,
        jsonrpc = JSONRPC_VERSION,
    )
}

/**
 * Decodes a JSON-RPC request into a protocol-specific [Request].
 *
 * @return The decoded [Request] or null
 */
@Deprecated(
    message = "Use `fromJSON` instead",
    replaceWith = ReplaceWith("fromJSON", "io.modelcontextprotocol.kotlin.sdk.types.fromJSON"),
    level = DeprecationLevel.WARNING,
)
internal fun JSONRPCRequest.fromJSON(): Request {
    val requestData = JsonObject(params.jsonObject + ("method" to JsonPrimitive(method)))
    val deserializer = selectRequestDeserializer(method)
    return McpJson.decodeFromJsonElement(deserializer, requestData)
}

/**
 * A custom request with a specified method.
 *
 * @param method The method associated with the request.
 */
@Serializable
@Deprecated(
    message = "Use `CustomRequest` instead",
    replaceWith = ReplaceWith("CustomRequest", "io.modelcontextprotocol.kotlin.sdk.types.CustomRequest"),
    level = DeprecationLevel.WARNING,
)
public open class CustomRequest(override val method: Method) : Request

/**
 * Represents a notification in the protocol.
 */
@Serializable(with = NotificationPolymorphicSerializer::class)
@Deprecated(
    message = "Use `Notification` instead",
    replaceWith = ReplaceWith("Notification", "io.modelcontextprotocol.kotlin.sdk.types.Notification"),
    level = DeprecationLevel.WARNING,
)
public sealed interface Notification {
    public val method: Method
    public val params: NotificationParams?
}

/**
 * Converts the notification to a JSON-RPC notification.
 *
 * @return The JSON-RPC notification representation.
 */
@Deprecated(
    message = "Use `toJSON` instead",
    replaceWith = ReplaceWith("toJSON", "io.modelcontextprotocol.kotlin.sdk.types.toJSON"),
    level = DeprecationLevel.WARNING,
)
public fun Notification.toJSON(): JSONRPCNotification = JSONRPCNotification(
    method = method.value,
    params = McpJson.encodeToJsonElement(params),
)

/**
 * Decodes a JSON-RPC notification into a protocol-specific [Notification].
 *
 * @return The decoded [Notification].
 */
@Deprecated(
    message = "Use `fromJSON` instead",
    replaceWith = ReplaceWith("toJSON", "io.modelcontextprotocol.kotlin.sdk.types.fromJSON"),
    level = DeprecationLevel.WARNING,
)
internal fun JSONRPCNotification.fromJSON(): Notification {
    val data = buildJsonObject {
        put("method", JsonPrimitive(method))
        put("params", params)
    }
    return McpJson.decodeFromJsonElement<Notification>(data)
}

/**
 * Represents the result of a request, including additional metadata.
 */
@Serializable(with = RequestResultPolymorphicSerializer::class)
@Deprecated(
    message = "Use `RequestResult` instead",
    replaceWith = ReplaceWith("RequestResult", "io.modelcontextprotocol.kotlin.sdk.types.RequestResult"),
    level = DeprecationLevel.WARNING,
)
public sealed interface RequestResult : WithMeta

/**
 * An empty result for a request containing optional metadata.
 *
 * @param _meta Additional metadata for the response. Defaults to an empty JSON object.
 */
@Serializable
@Deprecated(
    message = "Use `EmptyResult` instead",
    replaceWith = ReplaceWith("EmptyResult", "io.modelcontextprotocol.kotlin.sdk.types.EmptyResult"),
    level = DeprecationLevel.WARNING,
)
public data class EmptyRequestResult(override val _meta: JsonObject = EmptyJsonObject) :
    ServerResult,
    ClientResult

/**
 * A uniquely identifying ID for a request in JSON-RPC.
 */
@Serializable(with = RequestIdSerializer::class)
@Deprecated(
    message = "Use `RequestId` instead",
    replaceWith = ReplaceWith("RequestId", "io.modelcontextprotocol.kotlin.sdk.types.RequestId"),
    level = DeprecationLevel.WARNING,
)
public sealed interface RequestId {
    @Serializable
    public data class StringId(val value: String) : RequestId

    @Serializable
    public data class NumberId(val value: Long) : RequestId
}

/**
 * Represents a JSON-RPC message in the protocol.
 */
@Serializable(with = JSONRPCMessagePolymorphicSerializer::class)
@Deprecated(
    message = "Use `JSONRPCMessage` instead",
    replaceWith = ReplaceWith("JSONRPCMessage", "io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage"),
    level = DeprecationLevel.WARNING,
)
public sealed interface JSONRPCMessage

/**
 * A request that expects a response.
 */
@OptIn(ExperimentalAtomicApi::class)
@Serializable
@Deprecated(
    message = "Use `JSONRPCRequest` instead",
    replaceWith = ReplaceWith("JSONRPCRequest", "io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest"),
    level = DeprecationLevel.WARNING,
)
public data class JSONRPCRequest(
    val id: RequestId = RequestId.NumberId(REQUEST_MESSAGE_ID.incrementAndFetch()),
    val method: String,
    val params: JsonElement = EmptyJsonObject,
    val jsonrpc: String = JSONRPC_VERSION,
) : JSONRPCMessage

/**
 * A notification which does not expect a response.
 */
@Serializable
@Deprecated(
    message = "Use `JSONRPCNotification` instead",
    replaceWith = ReplaceWith("JSONRPCNotification", "io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification"),
    level = DeprecationLevel.WARNING,
)
public data class JSONRPCNotification(
    val method: String,
    val params: JsonElement = EmptyJsonObject,
    val jsonrpc: String = JSONRPC_VERSION,
) : JSONRPCMessage

/**
 * A successful (non-error) response to a request.
 */
@Serializable
@Deprecated(
    message = "Use `JSONRPCResponse` instead",
    replaceWith = ReplaceWith("JSONRPCResponse", "io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse"),
    level = DeprecationLevel.WARNING,
)
public class JSONRPCResponse(
    public val id: RequestId,
    public val jsonrpc: String = JSONRPC_VERSION,
    public val result: RequestResult? = null,
    public val error: JSONRPCError? = null,
) : JSONRPCMessage {

    public fun copy(
        id: RequestId = this.id,
        jsonrpc: String = this.jsonrpc,
        result: RequestResult? = this.result,
        error: JSONRPCError? = this.error,
    ): JSONRPCResponse = JSONRPCResponse(id, jsonrpc, result, error)
}

/**
 * An incomplete set of error codes that may appear in JSON-RPC responses.
 */
@Serializable(with = ErrorCodeSerializer::class)
@Deprecated(
    message = "Use `RPCError` instead",
    replaceWith = ReplaceWith("RPCError", "io.modelcontextprotocol.kotlin.sdk.types.RPCError"),
    level = DeprecationLevel.WARNING,
)
public sealed interface ErrorCode {
    public val code: Int

    @Serializable
    public enum class Defined(override val code: Int) : ErrorCode {
        // SDK error codes
        ConnectionClosed(-1),
        RequestTimeout(-2),

        // Standard JSON-RPC error codes
        ParseError(-32700),
        InvalidRequest(-32600),
        MethodNotFound(-32601),
        InvalidParams(-32602),
        InternalError(-32603),
    }

    @Serializable
    public data class Unknown(override val code: Int) : ErrorCode
}

/**
 * A response to a request that indicates an error occurred.
 */
@Serializable
@Deprecated(
    message = "Use `JSONRPCError` instead",
    replaceWith = ReplaceWith("JSONRPCError", "io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError"),
    level = DeprecationLevel.WARNING,
)
public data class JSONRPCError(val code: ErrorCode, val message: String, val data: JsonObject = EmptyJsonObject) :
    JSONRPCMessage

/**
 * Base interface for notification parameters with optional metadata.
 */
@Serializable
@Deprecated(
    message = "Use `NotificationParams` instead",
    replaceWith = ReplaceWith("NotificationParams", "io.modelcontextprotocol.kotlin.sdk.types.NotificationParams"),
    level = DeprecationLevel.WARNING,
)
public sealed interface NotificationParams : WithMeta

/* Cancellation */

/**
 * This notification can be sent by either side to indicate that it is cancelling a previously issued request.
 *
 * The request SHOULD still be in-flight, but due to communication latency, it is always possible that this notification MAY arrive after the request has already finished.
 *
 * This notification indicates that the result will be unused, so any associated processing SHOULD cease.
 *
 * A client MUST NOT attempt to cancel its `initialize` request.
 */
@Serializable
@Deprecated(
    message = "Use `CancelledNotification` instead",
    replaceWith = ReplaceWith(
        "CancelledNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.CancelledNotification",
    ),
    level = DeprecationLevel.WARNING,
)
public data class CancelledNotification(override val params: Params) :
    ClientNotification,
    ServerNotification {
    override val method: Method = Method.Defined.NotificationsCancelled

    @Serializable
    public data class Params(
        /**
         * The ID of the request to cancel.
         *
         * It MUST correspond to the ID of a request previously issued in the same direction.
         */
        val requestId: RequestId,
        /**
         * An optional string describing the reason for the cancellation. This MAY be logged or presented to the user.
         */
        val reason: String? = null,
        override val _meta: JsonObject = EmptyJsonObject,
    ) : NotificationParams
}

/* Initialization */

/**
 * Describes the name and version of an MCP implementation.
 */
@Serializable
@Deprecated(
    message = "Use `Implementation` instead",
    replaceWith = ReplaceWith("Implementation", "io.modelcontextprotocol.kotlin.sdk.types.Implementation"),
    level = DeprecationLevel.WARNING,
)
public data class Implementation(val name: String, val version: String)

/**
 * Capabilities a client may support.
 * Known capabilities are defined here, in this, but this is not a closed set:
 * any client can define its own, additional capabilities.
 */
@Serializable
@Deprecated(
    message = "Use `ClientCapabilities` instead",
    replaceWith = ReplaceWith("ClientCapabilities", "io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities"),
    level = DeprecationLevel.WARNING,
)
public data class ClientCapabilities(
    /**
     * Experimental, non-standard capabilities that the client supports.
     */
    val experimental: JsonObject? = EmptyJsonObject,
    /**
     * Present if the client supports sampling from an LLM.
     */
    val sampling: JsonObject? = EmptyJsonObject,
    /**
     * Present if the client supports listing roots.
     */
    val roots: Roots? = null,
    /**
     * Present if the client supports elicitation.
     */
    val elicitation: JsonObject? = null,
) {
    @Serializable
    public data class Roots(
        /**
         * Whether the client supports issuing notifications for changes to the root list.
         */
        val listChanged: Boolean?,
    )
}

/**
 * Represents a request sent by the client.
 */
// @Serializable(with = ClientRequestPolymorphicSerializer::class)
@Deprecated(
    message = "Use `ClientRequest` instead",
    replaceWith = ReplaceWith("ClientRequest", "io.modelcontextprotocol.kotlin.sdk.types.ClientRequest"),
    level = DeprecationLevel.WARNING,
)
public interface ClientRequest : Request

/**
 * Represents a notification sent by the client.
 */
@Serializable(with = ClientNotificationPolymorphicSerializer::class)
@Deprecated(
    message = "Use `ClientNotification` instead",
    replaceWith = ReplaceWith("ClientNotification", "io.modelcontextprotocol.kotlin.sdk.types.ClientNotification"),
    level = DeprecationLevel.WARNING,
)
public sealed interface ClientNotification : Notification

/**
 * Represents a result returned to the client.
 */
@Serializable(with = ClientResultPolymorphicSerializer::class)
@Deprecated(
    message = "Use `ClientResult` instead",
    replaceWith = ReplaceWith("ClientResult", "io.modelcontextprotocol.kotlin.sdk.types.ClientResult"),
    level = DeprecationLevel.WARNING,
)
public sealed interface ClientResult : RequestResult

/**
 * Represents a request sent by the server.
 */
// @Serializable(with = ServerRequestPolymorphicSerializer::class)
@Deprecated(
    message = "Use `ServerRequest` instead",
    replaceWith = ReplaceWith("ServerRequest", "io.modelcontextprotocol.kotlin.sdk.types.ServerRequest"),
    level = DeprecationLevel.WARNING,
)
public sealed interface ServerRequest : Request

/**
 * Represents a notification sent by the server.
 */
@Serializable(with = ServerNotificationPolymorphicSerializer::class)
@Deprecated(
    message = "Use `ServerNotification` instead",
    replaceWith = ReplaceWith("ServerNotification", "io.modelcontextprotocol.kotlin.sdk.types.ServerNotification"),
    level = DeprecationLevel.WARNING,
)
public sealed interface ServerNotification : Notification

/**
 * Represents a result returned by the server.
 */
@Serializable(with = ServerResultPolymorphicSerializer::class)
@Deprecated(
    message = "Use `ServerResult` instead",
    replaceWith = ReplaceWith("ServerResult", "io.modelcontextprotocol.kotlin.sdk.types.ServerResult"),
    level = DeprecationLevel.WARNING,
)
public sealed interface ServerResult : RequestResult

/**
 * Represents a request or notification for an unknown method.
 *
 * @param method The method that is unknown.
 */
@Serializable
@Deprecated(
    message = "This class will be removed",
    level = DeprecationLevel.WARNING,
)
public data class UnknownMethodRequestOrNotification(
    override val method: Method,
    override val params: NotificationParams? = null,
) : ClientNotification,
    ClientRequest,
    ServerNotification,
    ServerRequest

/**
 * This request is sent from the client to the server when it first connects, asking it to begin initialization.
 */
@Serializable
@Deprecated(
    message = "Use `InitializeRequest` instead",
    replaceWith = ReplaceWith("InitializeRequest", "io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest"),
    level = DeprecationLevel.WARNING,
)
public data class InitializeRequest(
    val protocolVersion: String,
    val capabilities: ClientCapabilities,
    val clientInfo: Implementation,
    override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest,
    WithMeta {
    override val method: Method = Method.Defined.Initialize
}

/**
 * Represents the capabilities that a server can support.
 *
 * @property experimental Experimental, non-standard capabilities that the server supports.
 * @property sampling Present if the client supports sampling from an LLM.
 * @property logging Present if the server supports sending log messages to the client.
 * @property prompts Capabilities related to prompt templates offered by the server.
 * @property resources Capabilities related to resources available on the server.
 * @property tools Capabilities related to tools that can be called on the server.
 */
@Serializable
@Deprecated(
    message = "Use `ServerCapabilities` instead",
    replaceWith = ReplaceWith("ServerCapabilities", "io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities"),
    level = DeprecationLevel.WARNING,
)
public data class ServerCapabilities(
    val experimental: JsonObject? = EmptyJsonObject,
    val sampling: JsonObject? = EmptyJsonObject,
    val logging: JsonObject? = EmptyJsonObject,
    val prompts: Prompts? = null,
    val resources: Resources? = null,
    val tools: Tools? = null,
) {
    /**
     * Capabilities related to prompt templates.
     *
     * @property listChanged Indicates if the server supports notifications when the prompt list changes.
     */
    @Serializable
    public data class Prompts(
        /**
         * Whether this server supports issuing notifications for changes to the prompt list.
         */
        val listChanged: Boolean?,
    )

    /**
     * Capabilities related to resources.
     *
     * @property subscribe Indicates if clients can subscribe to resource updates.
     * @property listChanged Indicates if the server supports notifications when the resource list changes.
     */
    @Serializable
    public data class Resources(
        /**
         * Whether this server supports clients subscribing to resource updates.
         */
        val subscribe: Boolean?,
        /**
         * Whether this server supports issuing notifications for changes to the resource list.
         */
        val listChanged: Boolean?,
    )

    /**
     * Capabilities related to tools.
     *
     * @property listChanged Indicates if the server supports notifications when the tool list changes.
     */
    @Serializable
    public data class Tools(
        /**
         * Whether this server supports issuing notifications for changes to the tool list.
         */
        val listChanged: Boolean?,
    )
}

/**
 * After receiving an initialized request from the client, the server sends this response.
 */
@Serializable
@Deprecated(
    message = "Use `InitializeResult` instead",
    replaceWith = ReplaceWith("InitializeResult", "io.modelcontextprotocol.kotlin.sdk.types.InitializeResult"),
    level = DeprecationLevel.WARNING,
)
public data class InitializeResult(
    /**
     * The version of the Model Context Protocol that the server wants to use. This may not match the version that the client requested. If the client cannot support this version, it MUST disconnect.
     */
    val protocolVersion: String = LATEST_PROTOCOL_VERSION,
    val capabilities: ServerCapabilities = ServerCapabilities(),
    val serverInfo: Implementation,
    /**
     * Optional instructions from the server to the client about how to use this server.
     */
    val instructions: String? = null,
    override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult

/**
 * This notification is sent from the client to the server after initialization has finished.
 */
@Serializable
@Deprecated(
    message = "Use `InitializedNotification` instead",
    replaceWith = ReplaceWith(
        "InitializedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification",
    ),
    level = DeprecationLevel.WARNING,
)
public data class InitializedNotification(override val params: Params = Params()) : ClientNotification {
    override val method: Method = Method.Defined.NotificationsInitialized

    @Serializable
    public data class Params(override val _meta: JsonObject = EmptyJsonObject) : NotificationParams
}

/* Ping */

/**
 * A ping, issued by either the server or the client, to check that the other party is still alive.
 * The receiver must promptly respond, or else it may be disconnected.
 */
@Serializable
@Deprecated(
    message = "Use `PingRequest` instead",
    replaceWith = ReplaceWith("PingRequest", "io.modelcontextprotocol.kotlin.sdk.types.PingRequest"),
    level = DeprecationLevel.WARNING,
)
public class PingRequest :
    ServerRequest,
    ClientRequest {
    override val method: Method = Method.Defined.Ping
}

/**
 * Represents the base interface for progress tracking.
 */
@Serializable
@Deprecated(
    message = "This interface will be removed",
    level = DeprecationLevel.WARNING,
)
public sealed interface ProgressBase {
    /**
     * The progress thus far. This should increase every time progress is made, even if the total is unknown.
     */
    public val progress: Double

    /**
     * Total number of items to a process (or total progress required), if known.
     */
    public val total: Double?

    /**
     * An optional message describing the current progress.
     */
    public val message: String?
}

/* Progress notifications */

/**
 * Represents a progress notification.
 *
 * @property progress The current progress value.
 * @property total The total progress required, if known.
 */
@Serializable
@Deprecated(
    message = "Use `Progress` instead",
    replaceWith = ReplaceWith("Progress", "io.modelcontextprotocol.kotlin.sdk.types.Progress"),
    level = DeprecationLevel.WARNING,
)
public open class Progress(
    /**
     * The progress thus far. This should increase every time progress is made, even if the total is unknown.
     */
    override val progress: Double,

    /**
     * Total number of items to a process (or total progress required), if known.
     */
    override val total: Double?,

    /**
     * An optional message describing the current progress.
     */
    override val message: String?,
) : ProgressBase

/**
 * An out-of-band notification used to inform the receiver of a progress update for a long-running request.
 */
@Serializable
@Deprecated(
    message = "Use `ProgressNotification` instead",
    replaceWith = ReplaceWith("ProgressNotification", "io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification"),
    level = DeprecationLevel.WARNING,
)
public data class ProgressNotification(override val params: Params) :
    ClientNotification,
    ServerNotification {
    override val method: Method = Method.Defined.NotificationsProgress

    @Serializable
    public data class Params(
        /**
         * The progress thus far. This should increase every time progress is made, even if the total is unknown.
         */
        override val progress: Double,
        /**
         * The progress token,
         * which was given in the initial request,
         * used to associate this notification with the request that is proceeding.
         */
        val progressToken: ProgressToken,
        /**
         * Total number of items to process (or total progress required), if known.
         */
        override val total: Double? = null,
        /**
         * An optional message describing the current progress.
         */
        override val message: String? = null,
        override val _meta: JsonObject = EmptyJsonObject,
    ) : NotificationParams,
        ProgressBase
}

/* Pagination */

/**
 * Represents a request supporting pagination.
 */
@Serializable
@Deprecated(
    message = "Use `PaginatedRequest` instead",
    replaceWith = ReplaceWith("PaginatedRequest", "io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequest"),
    level = DeprecationLevel.WARNING,
)
public sealed interface PaginatedRequest :
    Request,
    WithMeta {
    /**
     * The cursor indicating the pagination position.
     */
    public val cursor: Cursor?
    override val _meta: JsonObject
}

/**
 * Represents a paginated result of a request.
 */
@Serializable
@Deprecated(
    message = "Use `PaginatedResult` instead",
    replaceWith = ReplaceWith("PaginatedResult", "io.modelcontextprotocol.kotlin.sdk.types.PaginatedResult"),
    level = DeprecationLevel.WARNING,
)
public sealed interface PaginatedResult : RequestResult {
    /**
     * An opaque token representing the pagination position after the last returned result.
     * If present, there may be more results available.
     */
    public val nextCursor: Cursor?
}

/* Resources */

/**
 * The contents of a specific resource or sub-resource.
 */
@Serializable(with = ResourceContentsPolymorphicSerializer::class)
@Deprecated(
    message = "Use `ResourceContents` instead",
    replaceWith = ReplaceWith("ResourceContents", "io.modelcontextprotocol.kotlin.sdk.types.ResourceContents"),
    level = DeprecationLevel.WARNING,
)
public sealed interface ResourceContents {
    /**
     * The URI of this resource.
     */
    public val uri: String

    /**
     * The MIME type of this resource, if known.
     */
    public val mimeType: String?
}

/**
 * Represents the text contents of a resource.
 *
 * @property text The text of the item. This must only be set if the item can actually be represented as text (not binary data).
 */
@Serializable
@Deprecated(
    message = "Use `TextResourceContents` instead",
    replaceWith = ReplaceWith("TextResourceContents", "io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents"),
    level = DeprecationLevel.WARNING,
)
public data class TextResourceContents(val text: String, override val uri: String, override val mimeType: String?) :
    ResourceContents

/**
 * Represents the binary contents of a resource encoded as a base64 string.
 *
 * @property blob A base64-encoded string representing the binary data of the item.
 */
@Serializable
@Deprecated(
    message = "Use `BlobResourceContents` instead",
    replaceWith = ReplaceWith("BlobResourceContents", "io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents"),
    level = DeprecationLevel.WARNING,
)
public data class BlobResourceContents(val blob: String, override val uri: String, override val mimeType: String?) :
    ResourceContents

/**
 * Represents resource contents with unknown or unspecified data.
 */
@Serializable
@Deprecated(
    message = "Use `UnknownResourceContents` instead",
    replaceWith = ReplaceWith(
        "UnknownResourceContents",
        "io.modelcontextprotocol.kotlin.sdk.types.UnknownResourceContents",
    ),
    level = DeprecationLevel.WARNING,
)
public data class UnknownResourceContents(override val uri: String, override val mimeType: String?) : ResourceContents

/**
 * A known resource that the server is capable of reading.
 */
@Serializable
@Deprecated(
    message = "Use `Resource` instead",
    replaceWith = ReplaceWith("Resource", "io.modelcontextprotocol.kotlin.sdk.types.Resource"),
    level = DeprecationLevel.WARNING,
)
public data class Resource(
    /**
     * The URI of this resource.
     */
    val uri: String,
    /**
     * A human-readable name for this resource.
     *
     * Clients can use this to populate UI elements.
     */
    val name: String,
    /**
     * A description of what this resource represents.
     *
     * Clients can use this to improve the LLM's understanding of available resources.
     * It can be thought of as a "hint" to the model.
     */
    val description: String?,
    /**
     * The MIME type of this resource, if known.
     */
    val mimeType: String?,
    /**
     * The optional human-readable name of this resource for display purposes.
     */
    val title: String? = null,
    /**
     * The optional size of this resource in bytes, if known.
     */
    val size: Long? = null,
    /**
     * Optional annotations for the client.
     */
    val annotations: Annotations? = null,
)

/**
 * A template description for resources available on the server.
 */
@Serializable
@Deprecated(
    message = "Use `ResourceTemplate` instead",
    replaceWith = ReplaceWith("ResourceTemplate", "io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate"),
    level = DeprecationLevel.WARNING,
)
public data class ResourceTemplate(
    /**
     * A URI template (according to RFC 6570) that can be used to construct resource URIs.
     */
    val uriTemplate: String,
    /**
     * A human-readable name for the type of resource this template refers to.
     *
     * Clients can use this to populate UI elements.
     */
    val name: String,
    /**
     * A description of what this template is for.
     *
     * Clients can use this to improve the LLM's understanding of available resources.
     * It can be thought of as a "hint" to the model.
     */
    val description: String?,
    /**
     * The MIME type for all resources that match this template. This should only be included if all resources matching this template have the same type.
     */
    val mimeType: String?,
    /**
     * The optional human-readable name of this resource for display purposes.
     */
    val title: String? = null,
    /**
     * Optional annotations for the client.
     */
    val annotations: Annotations? = null,
)

/**
 * Sent from the client to request a list of resources the server has.
 */
@Serializable
@Deprecated(
    message = "Use `ListResourcesRequest` instead",
    replaceWith = ReplaceWith("ListResourcesRequest", "io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest"),
    level = DeprecationLevel.WARNING,
)
public data class ListResourcesRequest(
    override val cursor: Cursor? = null,
    override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest,
    PaginatedRequest {
    override val method: Method = Method.Defined.ResourcesList
}

/**
 * The server's response to a resources/list request from the client.
 */
@Serializable
@Deprecated(
    message = "Use `ListResourcesResult` instead",
    replaceWith = ReplaceWith("ListResourcesResult", "io.modelcontextprotocol.kotlin.sdk.types.ListResourcesResult"),
    level = DeprecationLevel.WARNING,
)
public class ListResourcesResult(
    public val resources: List<Resource>,
    override val nextCursor: Cursor? = null,
    override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult,
    PaginatedResult

/**
 * Sent from the client to request a list of resource templates the server has.
 */
@Serializable
@Deprecated(
    message = "Use `ListResourceTemplatesRequest` instead",
    replaceWith = ReplaceWith(
        "ListResourceTemplatesRequest",
        "io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesRequest",
    ),
    level = DeprecationLevel.WARNING,
)
public data class ListResourceTemplatesRequest(
    override val cursor: Cursor?,
    override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest,
    PaginatedRequest {
    override val method: Method = Method.Defined.ResourcesTemplatesList
}

/**
 * The server's response to a resources/templates/list request from the client.
 */
@Serializable
@Deprecated(
    message = "Use `ListResourceTemplatesResult` instead",
    replaceWith = ReplaceWith(
        "ListResourceTemplatesResult",
        "io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesResult",
    ),
    level = DeprecationLevel.WARNING,
)
public class ListResourceTemplatesResult(
    public val resourceTemplates: List<ResourceTemplate>,
    override val nextCursor: Cursor? = null,
    override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult,
    PaginatedResult

/**
 * Sent from the client to the server to read a specific resource URI.
 */
@Serializable
@Deprecated(
    message = "Use `ReadResourceRequest` instead",
    replaceWith = ReplaceWith("ReadResourceRequest", "io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest"),
    level = DeprecationLevel.WARNING,
)
public data class ReadResourceRequest(val uri: String, override val _meta: JsonObject = EmptyJsonObject) :
    ClientRequest,
    WithMeta {
    override val method: Method = Method.Defined.ResourcesRead
}

/**
 * The server's response to a resources/read request from the client.
 */
@Serializable
@Deprecated(
    message = "Use `ReadResourceResult` instead",
    replaceWith = ReplaceWith("ReadResourceResult", "io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult"),
    level = DeprecationLevel.WARNING,
)
public class ReadResourceResult(
    public val contents: List<ResourceContents>,
    override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult

/**
 * An optional notification from the server to the client,
 * informing it that the list of resources it can read from has changed.
 * Servers may issue this without any previous subscription from the client.
 */
@Serializable
@Deprecated(
    message = "Use `ResourceListChangedNotification` instead",
    replaceWith = ReplaceWith(
        "ResourceListChangedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification",
    ),
    level = DeprecationLevel.WARNING,
)
public data class ResourceListChangedNotification(override val params: Params = Params()) : ServerNotification {
    override val method: Method = Method.Defined.NotificationsResourcesListChanged

    @Serializable
    public data class Params(override val _meta: JsonObject = EmptyJsonObject) : NotificationParams
}

/**
 * Sent from the client to request resources/updated notifications from the server whenever a particular resource changes.
 */
@Serializable
@Deprecated(
    message = "Use `SubscribeRequest` instead",
    replaceWith = ReplaceWith("SubscribeRequest", "io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequest"),
    level = DeprecationLevel.WARNING,
)
public data class SubscribeRequest(
    /**
     * The URI of the resource to subscribe to. The URI can use any protocol; it is up to the server how to interpret it.
     */
    val uri: String,
    override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest,
    WithMeta {
    override val method: Method = Method.Defined.ResourcesSubscribe
}

/**
 * Sent from the client to request cancellation of resources/updated notifications from the server. This should follow a previous resources/subscribe request.
 */
@Serializable
@Deprecated(
    message = "Use `UnsubscribeRequest` instead",
    replaceWith = ReplaceWith("UnsubscribeRequest", "io.modelcontextprotocol.kotlin.sdk.types.UnsubscribeRequest"),
    level = DeprecationLevel.WARNING,
)
public data class UnsubscribeRequest(
    /**
     * The URI of the resource to unsubscribe from.
     */
    val uri: String,
    override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest,
    WithMeta {
    override val method: Method = Method.Defined.ResourcesUnsubscribe
}

/**
 * A notification from the server to the client, informing it that a resource has changed and may need to be read again. This should only be sent if the client previously sent a resources/subscribe request.
 */
@Serializable
@Deprecated(
    message = "Use `ResourceUpdatedNotification` instead",
    replaceWith = ReplaceWith(
        "ResourceUpdatedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification",
    ),
    level = DeprecationLevel.WARNING,
)
public data class ResourceUpdatedNotification(override val params: Params) : ServerNotification {
    override val method: Method = Method.Defined.NotificationsResourcesUpdated

    @Serializable
    public data class Params(
        /**
         * The URI of the resource that has been updated. This might be a sub-resource of the one that the client actually subscribed to.
         */
        val uri: String,
        override val _meta: JsonObject = EmptyJsonObject,
    ) : NotificationParams
}

/* Prompts */

/**
 * Describes an argument that a prompt can accept.
 */
@Serializable
@Deprecated(
    message = "Use `PromptArgument` instead",
    replaceWith = ReplaceWith("PromptArgument", "io.modelcontextprotocol.kotlin.sdk.types.PromptArgument"),
    level = DeprecationLevel.WARNING,
)
public data class PromptArgument(
    /**
     * The name of the argument.
     */
    val name: String,
    /**
     * A human-readable description of the argument.
     */
    val description: String?,
    /**
     * Whether this argument must be provided.
     */
    val required: Boolean?,
)

/**
 * A prompt or prompt template that the server offers.
 */
@Serializable
@Deprecated(
    message = "Use `Prompt` instead",
    replaceWith = ReplaceWith("Prompt", "io.modelcontextprotocol.kotlin.sdk.types.Prompt"),
    level = DeprecationLevel.WARNING,
)
public class Prompt(
    /**
     * The name of the prompt or prompt template.
     */
    public val name: String,
    /**
     * An optional description of what this prompt provides
     */
    public val description: String?,
    /**
     * A list of arguments to use for templating the prompt.
     */
    public val arguments: List<PromptArgument>?,
)

/**
 * Sent from the client to request a list of prompts and prompt templates the server has.
 */
@Serializable
@Deprecated(
    message = "Use `ListPromptsRequest` instead",
    replaceWith = ReplaceWith("ListPromptsRequest", "io.modelcontextprotocol.kotlin.sdk.types.ListPromptsRequest"),
    level = DeprecationLevel.WARNING,
)
public data class ListPromptsRequest(
    override val cursor: Cursor? = null,
    override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest,
    PaginatedRequest {
    override val method: Method = Method.Defined.PromptsList
}

/**
 * The server's response to a prompts/list request from the client.
 */
@Serializable
@Deprecated(
    message = "Use `ListPromptsResult` instead",
    replaceWith = ReplaceWith("ListPromptsResult", "io.modelcontextprotocol.kotlin.sdk.types.ListPromptsResult"),
    level = DeprecationLevel.WARNING,
)
public class ListPromptsResult(
    public val prompts: List<Prompt>,
    override val nextCursor: Cursor? = null,
    override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult,
    PaginatedResult

/**
 * Used by the client to get a prompt provided by the server.
 */
@Serializable
@Deprecated(
    message = "Use `GetPromptRequest` instead",
    replaceWith = ReplaceWith("GetPromptRequest", "io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest"),
    level = DeprecationLevel.WARNING,
)
public data class GetPromptRequest(
    /**
     * The name of the prompt or prompt template.
     */
    val name: String,

    /**
     * Arguments to use for templating the prompt.
     */
    val arguments: Map<String, String>?,

    override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest,
    WithMeta {
    override val method: Method = Method.Defined.PromptsGet
}

/**
 * Represents the content of a prompt message.
 */
@Serializable(with = PromptMessageContentPolymorphicSerializer::class)
@Deprecated(
    message = "Use `ContentBlock` instead",
    replaceWith = ReplaceWith("ContentBlock"),
    level = DeprecationLevel.WARNING,
)
public sealed interface PromptMessageContent {
    public val type: String
}

/**
 * Represents prompt message content that is either text, image or audio.
 */
@Serializable(with = PromptMessageContentMultimodalPolymorphicSerializer::class)
@Deprecated(
    message = "Use `MediaContent` instead",
    replaceWith = ReplaceWith("MediaContent"),
    level = DeprecationLevel.WARNING,
)
public sealed interface PromptMessageContentMultimodal : PromptMessageContent

/**
 * Text provided to or from an LLM.
 */
@Serializable
@Deprecated(
    message = "Use `TextContent` instead",
    replaceWith = ReplaceWith("TextContent", "io.modelcontextprotocol.kotlin.sdk.types.TextContent"),
    level = DeprecationLevel.WARNING,
)
public data class TextContent(
    /**
     * The text content of the message.
     */
    val text: String? = null,

    /**
     * Optional annotations for the client.
     */
    val annotations: Annotations? = null,
) : PromptMessageContentMultimodal {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "text"
    }
}

/**
 * An image provided to or from an LLM.
 */
@Serializable
@Deprecated(
    message = "Use `ImageContent` instead",
    replaceWith = ReplaceWith("ImageContent", "io.modelcontextprotocol.kotlin.sdk.types.ImageContent"),
    level = DeprecationLevel.WARNING,
)
public data class ImageContent(
    /**
     * The base64-encoded image data.
     */
    val data: String,

    /**
     * The MIME type of the image. Different providers may support different image types.
     */
    val mimeType: String,

    /**
     * Optional annotations for the client.
     */
    val annotations: Annotations? = null,
) : PromptMessageContentMultimodal {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "image"
    }
}

/**
 * Audio provided to or from an LLM.
 */
@Serializable
@Deprecated(
    message = "Use `AudioContent` instead",
    replaceWith = ReplaceWith("AudioContent", "io.modelcontextprotocol.kotlin.sdk.types.AudioContent"),
    level = DeprecationLevel.WARNING,
)
public data class AudioContent(
    /**
     * The base64-encoded audio data.
     */
    val data: String,

    /**
     * The MIME type of the audio. Different providers may support different audio types.
     */
    val mimeType: String,

    /**
     * Optional annotations for the client.
     */
    val annotations: Annotations? = null,
) : PromptMessageContentMultimodal {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "audio"
    }
}

/**
 * Unknown content provided to or from an LLM.
 */
@Serializable
@Deprecated(
    message = "This class will be removed.",
    replaceWith = ReplaceWith("MediaContent"),
    level = DeprecationLevel.WARNING,
)
public data class UnknownContent(override val type: String) : PromptMessageContentMultimodal

/**
 * The contents of a resource, embedded into a prompt or tool call result.
 */
@Serializable
@Deprecated(
    message = "Use `EmbeddedResource` instead",
    replaceWith = ReplaceWith("EmbeddedResource", "io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource"),
    level = DeprecationLevel.WARNING,
)
public data class EmbeddedResource(
    /**
     * The contents of the embedded resource.
     */
    val resource: ResourceContents,

    /**
     * Optional annotations for the client.
     */
    val annotations: Annotations? = null,
) : PromptMessageContent {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "resource"
    }
}

/**
 * Enum representing the role of a participant.
 */
@Suppress("EnumEntryName")
@Serializable
@Deprecated(
    message = "Use `Role` instead",
    replaceWith = ReplaceWith("Role", "io.modelcontextprotocol.kotlin.sdk.types.Role"),
    level = DeprecationLevel.WARNING,
)
public enum class Role {
    user,
    assistant,
}

/**
 * Optional annotations for the client.
 * The client can use annotations to inform how objects are used or displayed.
 */
@Serializable
@Deprecated(
    message = "Use `Annotations` instead",
    replaceWith = ReplaceWith("Annotations", "io.modelcontextprotocol.kotlin.sdk.types.Annotations"),
    level = DeprecationLevel.WARNING,
)
public data class Annotations(
    /**
     * Describes who the intended customer of this object or data is.
     */
    val audience: List<Role>?,
    /**
     * The moment the resource was last modified.
     */
    val lastModified: String?,
    /**
     * Describes how important this data is for operating the server.
     *
     * A value of 1.0 means "most important", and indicates that the data is effectively required,
     * while 0.0 means "less important", and indicates that the data is entirely optional.
     */
    val priority: Double?,
) {
    init {
        require(priority == null || priority in 0.0..1.0) { "Priority must be between 0.0 and 1.0" }
    }
}

/**
 * Describes a message returned as part of a prompt.
 */
@Serializable
@Deprecated(
    message = "Use `PromptMessage` instead",
    replaceWith = ReplaceWith("PromptMessage", "io.modelcontextprotocol.kotlin.sdk.types.PromptMessage"),
    level = DeprecationLevel.WARNING,
)
public data class PromptMessage(val role: Role, val content: PromptMessageContent)

/**
 * The server's response to a prompts/get request from the client.
 */
@Serializable
@Deprecated(
    message = "Use `GetPromptResult` instead",
    replaceWith = ReplaceWith("GetPromptResult", "io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult"),
    level = DeprecationLevel.WARNING,
)
public class GetPromptResult(
    /**
     * An optional description for the prompt.
     */
    public val description: String?,
    public val messages: List<PromptMessage>,
    override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult

/**
 * An optional notification from the server to the client, informing it that the list of prompts it offers has changed.
 * Servers may issue this without any previous subscription from the client.
 */
@Serializable
@Deprecated(
    message = "Use `PromptListChangedNotification` instead",
    replaceWith = ReplaceWith(
        "PromptListChangedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification",
    ),
    level = DeprecationLevel.WARNING,
)
public data class PromptListChangedNotification(override val params: Params = Params()) : ServerNotification {
    override val method: Method = Method.Defined.NotificationsPromptsListChanged

    @Serializable
    public data class Params(override val _meta: JsonObject = EmptyJsonObject) : NotificationParams
}

/* Tools */

/**
 * Additional properties describing a Tool to clients.
 *
 * NOTE: all properties in ToolAnnotations are **hints**.
 * They are not guaranteed to provide a faithful description of
 * tool behavior (including descriptive properties like `title`).
 *
 * Clients should never make tool use decisions based on ToolAnnotations
 * received from untrusted servers.
 */
@Serializable
@Deprecated(
    message = "Use `ToolAnnotations` instead",
    replaceWith = ReplaceWith("ToolAnnotations", "io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations"),
    level = DeprecationLevel.WARNING,
)
public data class ToolAnnotations(
    /**
     * A human-readable title for the tool.
     */
    val title: String?,
    /**
     * If true, the tool does not modify its environment.
     *
     * Default: false
     */
    val readOnlyHint: Boolean? = false,
    /**
     * If true, the tool may perform destructive updates to its environment.
     * If false, the tool performs only additive updates.
     *
     * (This property is meaningful only when `readOnlyHint == false`)
     *
     * Default: true
     */
    val destructiveHint: Boolean? = true,
    /**
     * If true, calling the tool repeatedly with the same arguments
     * will have no additional effect on the its environment.
     *
     * (This property is meaningful only when `readOnlyHint == false`)
     *
     * Default: false
     */
    val idempotentHint: Boolean? = false,
    /**
     * If true, this tool may interact with an "open world" of external
     * entities. If false, the tool's domain of interaction is closed.
     * For example, the world of a web search tool is open, whereas that
     * of a memory tool is not.
     *
     * Default: true
     */
    val openWorldHint: Boolean? = true,
)

/**
 * Definition for a tool the client can call.
 */
@Serializable
@Deprecated(
    message = "Use `Tool` instead",
    replaceWith = ReplaceWith("Tool", "io.modelcontextprotocol.kotlin.sdk.types.Tool"),
    level = DeprecationLevel.WARNING,
)
public data class Tool(
    /**
     * The name of the tool.
     */
    val name: String,
    /**
     * The title of the tool.
     */
    val title: String?,
    /**
     * A human-readable description of the tool.
     */
    val description: String?,
    /**
     * A JSON object defining the expected parameters for the tool.
     */
    val inputSchema: Input,
    /**
     * An optional JSON object defining the expected output schema for the tool.
     */
    val outputSchema: Output?,
    /**
     * Optional additional tool information.
     */
    val annotations: ToolAnnotations?,

    /**
     * Optional metadata for the tool.
     */
    override val _meta: JsonObject = EmptyJsonObject,
) : WithMeta {
    @Serializable
    public data class Input(val properties: JsonObject = EmptyJsonObject, val required: List<String>? = null) {
        @OptIn(ExperimentalSerializationApi::class)
        @EncodeDefault
        val type: String = "object"
    }

    @Serializable
    public data class Output(val properties: JsonObject = EmptyJsonObject, val required: List<String>? = null) {
        @OptIn(ExperimentalSerializationApi::class)
        @EncodeDefault
        val type: String = "object"
    }
}

/**
 * Sent from the client to request a list of tools the server has.
 */
@Serializable
@Deprecated(
    message = "Use `ListToolsRequest` instead",
    replaceWith = ReplaceWith("ListToolsRequest", "io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest"),
    level = DeprecationLevel.WARNING,
)
public data class ListToolsRequest(
    override val cursor: Cursor? = null,
    override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest,
    PaginatedRequest {
    override val method: Method = Method.Defined.ToolsList
}

/**
 * The server's response to a tools/list request from the client.
 */
@Serializable
@Deprecated(
    message = "Use `ListToolsResult` instead",
    replaceWith = ReplaceWith("ListToolsResult", "io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult"),
    level = DeprecationLevel.WARNING,
)
public class ListToolsResult(
    public val tools: List<Tool>,
    override val nextCursor: Cursor?,
    override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult,
    PaginatedResult

/**
 * The server's response to a tool call.
 */
@Serializable
@Deprecated(
    message = "This interface will be removed.",
    replaceWith = ReplaceWith("ToolCallResult"),
    level = DeprecationLevel.WARNING,
)
public sealed interface CallToolResultBase : ServerResult {
    public val content: List<PromptMessageContent>
    public val structuredContent: JsonObject?
    public val isError: Boolean? get() = false
}

/**
 * The server's response to a tool call.
 */
@Serializable
@Deprecated(
    message = "Use `CallToolResult` instead",
    replaceWith = ReplaceWith("CallToolResult", "io.modelcontextprotocol.kotlin.sdk.types.CallToolResult"),
    level = DeprecationLevel.WARNING,
)
public class CallToolResult(
    override val content: List<PromptMessageContent>,
    override val structuredContent: JsonObject? = null,
    override val isError: Boolean? = false,
    override val _meta: JsonObject = EmptyJsonObject,
) : CallToolResultBase

/**
 * [CallToolResult] extended with backwards compatibility to protocol version 2024-11-05.
 */
@Serializable
@Deprecated(
    message = "This class will be removed.",
    replaceWith = ReplaceWith("ToolCallResult"),
    level = DeprecationLevel.WARNING,
)
public class CompatibilityCallToolResult(
    override val content: List<PromptMessageContent>,
    override val structuredContent: JsonObject? = null,
    override val isError: Boolean? = false,
    override val _meta: JsonObject = EmptyJsonObject,
    public val toolResult: JsonObject = EmptyJsonObject,
) : CallToolResultBase

/**
 * Used by the client to invoke a tool provided by the server.
 */
@Serializable
@Deprecated(
    message = "Use `CallToolRequest` instead",
    replaceWith = ReplaceWith("CallToolRequest", "io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest"),
    level = DeprecationLevel.WARNING,
)
public data class CallToolRequest(
    val name: String,
    val arguments: JsonObject = EmptyJsonObject,
    override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest,
    WithMeta {
    override val method: Method = Method.Defined.ToolsCall
}

/**
 * An optional notification from the server to the client, informing it that the list of tools it offers has changed.
 * Servers may issue this without any previous subscription from the client.
 */
@Serializable
@Deprecated(
    message = "Use `ToolListChangedNotification` instead",
    replaceWith = ReplaceWith(
        "ToolListChangedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification",
    ),
    level = DeprecationLevel.WARNING,
)
public data class ToolListChangedNotification(override val params: Params = Params()) : ServerNotification {
    override val method: Method = Method.Defined.NotificationsToolsListChanged

    @Serializable
    public data class Params(override val _meta: JsonObject = EmptyJsonObject) : NotificationParams
}

/* Logging */

/**
 * The severity of a log message.
 */
@Suppress("EnumEntryName")
@Serializable
@Deprecated(
    message = "Use `LoggingLevel` instead",
    replaceWith = ReplaceWith("LoggingLevel", "io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel"),
    level = DeprecationLevel.WARNING,
)
public enum class LoggingLevel {
    debug,
    info,
    notice,
    warning,
    error,
    critical,
    alert,
    emergency,
}

/**
 * Notification of a log message passed from server to client.
 * If no logging level request has been sent from the client,
 * the server MAY decide which messages to send automatically.
 */
@Serializable
@Deprecated(
    message = "Use `LoggingMessageNotification` instead",
    replaceWith = ReplaceWith(
        "LoggingMessageNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification",
    ),
    level = DeprecationLevel.WARNING,
)
public data class LoggingMessageNotification(override val params: Params) : ServerNotification {
    override val method: Method = Method.Defined.NotificationsMessage

    @Serializable
    public data class Params(
        /**
         * The severity of this log message.
         */
        val level: LoggingLevel,
        /**
         * An optional name of the logger issuing this message.
         */
        val logger: String? = null,
        /**
         * The data to be logged, such as a string message or an object. Any JSON serializable type is allowed here.
         */
        val data: JsonElement,
        override val _meta: JsonObject = EmptyJsonObject,
    ) : NotificationParams

    /**
     * A request from the client to the server to enable or adjust logging.
     */
    @Serializable
    public data class SetLevelRequest(
        /**
         * The level of logging that the client wants to receive from the server. The server should send all logs at this level and higher (i.e., more severe) to the client as notifications/logging/message.
         */
        val level: LoggingLevel,
        override val _meta: JsonObject = EmptyJsonObject,
    ) : ClientRequest,
        WithMeta {
        override val method: Method = Method.Defined.LoggingSetLevel
    }
}

/* Sampling */

/**
 * Hints to use for model selection.
 */
@Serializable
@Deprecated(
    message = "Use `ModelHint` instead",
    replaceWith = ReplaceWith("ModelHint", "io.modelcontextprotocol.kotlin.sdk.types.ModelHint"),
    level = DeprecationLevel.WARNING,
)
public data class ModelHint(
    /**
     * A hint for a model name.
     */
    val name: String?,
)

/**
 * The server's preferences for model selection, requested by the client during sampling.
 */
@Suppress("CanBeParameter")
@Serializable
@Deprecated(
    message = "Use `ModelPreferences` instead",
    replaceWith = ReplaceWith("ModelPreferences", "io.modelcontextprotocol.kotlin.sdk.types.ModelPreferences"),
    level = DeprecationLevel.WARNING,
)
public class ModelPreferences(
    /**
     * Optional hints to use for model selection.
     */
    public val hints: List<ModelHint>?,
    /**
     * How much to prioritize cost when selecting a model.
     */
    public val costPriority: Double?,
    /**
     * How much to prioritize sampling speed (latency) when selecting a model.
     */
    public val speedPriority: Double?,
    /**
     * How much to prioritize intelligence and capabilities when selecting a model.
     */
    public val intelligencePriority: Double?,
) {
    init {
        require(costPriority == null || costPriority in 0.0..1.0) {
            "costPriority must be in 0.0 <= x <= 1.0 value range"
        }

        require(speedPriority == null || speedPriority in 0.0..1.0) {
            "costPriority must be in 0.0 <= x <= 1.0 value range"
        }

        require(intelligencePriority == null || intelligencePriority in 0.0..1.0) {
            "intelligencePriority must be in 0.0 <= x <= 1.0 value range"
        }
    }
}

/**
 * Describes a message issued to or received from an LLM API.
 */
@Serializable
@Deprecated(
    message = "Use `SamplingMessage` instead",
    replaceWith = ReplaceWith("SamplingMessage", "io.modelcontextprotocol.kotlin.sdk.types.SamplingMessage"),
    level = DeprecationLevel.WARNING,
)
public data class SamplingMessage(val role: Role, val content: PromptMessageContentMultimodal)

/**
 * A request from the server to sample an LLM via the client.
 * The client has full discretion over which model to select.
 * The client should also inform the user before beginning sampling to allow them to inspect the request
 * (human in the loop) and decide whether to approve it.
 */
@Serializable
@Deprecated(
    message = "Use `CreateMessageRequest` instead",
    replaceWith = ReplaceWith("CreateMessageRequest", "io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest"),
    level = DeprecationLevel.WARNING,
)
public data class CreateMessageRequest(
    val messages: List<SamplingMessage>,
    /**
     * An optional system prompt the server wants to use it for sampling. The client MAY modify or omit this prompt.
     */
    val systemPrompt: String?,
    /**
     * A request to include context from one or more MCP servers (including the caller), to be attached to the prompt. The client MAY ignore this request.
     */
    val includeContext: IncludeContext?,
    val temperature: Double?,
    /**
     * The maximum number of tokens to sample, as requested by the server. The client MAY choose to sample fewer tokens than requested.
     */
    val maxTokens: Int,
    val stopSequences: List<String>?,
    /**
     * Optional metadata to pass through to the LLM provider. The format of this metadata is provider-specific.
     */
    val metadata: JsonObject = EmptyJsonObject,
    /**
     * The server's preferences for which model to select.
     */
    val modelPreferences: ModelPreferences?,
    override val _meta: JsonObject = EmptyJsonObject,
) : ServerRequest,
    WithMeta {
    override val method: Method = Method.Defined.SamplingCreateMessage

    @Serializable
    public enum class IncludeContext { none, thisServer, allServers }
}

@Serializable(with = StopReasonSerializer::class)
@Deprecated(
    message = "Use `StopReason` instead",
    replaceWith = ReplaceWith("StopReason", "io.modelcontextprotocol.kotlin.sdk.types.StopReason"),
    level = DeprecationLevel.WARNING,
)
public sealed interface StopReason {
    public val value: String

    @Serializable
    public data object EndTurn : StopReason {
        override val value: String = "endTurn"
    }

    @Serializable
    public data object StopSequence : StopReason {
        override val value: String = "stopSequence"
    }

    @Serializable
    public data object MaxTokens : StopReason {
        override val value: String = "maxTokens"
    }

    @Serializable
    @JvmInline
    public value class Other(override val value: String) : StopReason
}

/**
 * The client's response to a sampling/create_message request from the server.
 * The client should inform the user before returning the sampled message to allow them to inspect the response
 * (human in the loop) and decide whether to allow the server to see it.
 */
@Serializable
@Deprecated(
    message = "Use `CreateMessageResult` instead",
    replaceWith = ReplaceWith("CreateMessageResult", "io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult"),
    level = DeprecationLevel.WARNING,
)
public data class CreateMessageResult(
    /**
     * The name of the model that generated the message.
     */
    val model: String,
    /**
     * The reason why sampling stopped.
     */
    val stopReason: StopReason? = null,
    val role: Role,
    val content: PromptMessageContentMultimodal,
    override val _meta: JsonObject = EmptyJsonObject,
) : ClientResult

/* Autocomplete */
@Serializable(with = ReferencePolymorphicSerializer::class)
@Deprecated(
    message = "Use `Reference` instead",
    replaceWith = ReplaceWith("Reference", "io.modelcontextprotocol.kotlin.sdk.types.Reference"),
    level = DeprecationLevel.WARNING,
)
public sealed interface Reference {
    public val type: String
}

/**
 * A reference to a resource or resource template definition.
 */
@Serializable
@Deprecated(
    message = "Use `ResourceTemplateReference` instead",
    replaceWith = ReplaceWith(
        "ResourceTemplateReference",
        "io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplateReference",
    ),
    level = DeprecationLevel.WARNING,
)
public data class ResourceTemplateReference(
    /**
     * The URI or URI template of the resource.
     */
    val uri: String,
) : Reference {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "ref/resource"
    }
}

/**
 * Identifies a prompt.
 */
@Serializable
@Deprecated(
    message = "Use `PromptReference` instead",
    replaceWith = ReplaceWith("PromptReference", "io.modelcontextprotocol.kotlin.sdk.types.PromptReference"),
    level = DeprecationLevel.WARNING,
)
public data class PromptReference(
    /**
     * The name of the prompt or prompt template
     */
    val name: String,
) : Reference {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "ref/prompt"
    }
}

/**
 * Identifies a prompt.
 */
@Serializable
@Deprecated(
    message = "This class will be removed.",
    level = DeprecationLevel.WARNING,
)
public data class UnknownReference(override val type: String) : Reference

/**
 * A request from the client to the server to ask for completion options.
 */
@Serializable
@Deprecated(
    message = "Use `CompleteRequest` instead",
    replaceWith = ReplaceWith("CompleteRequest", "io.modelcontextprotocol.kotlin.sdk.types.CompleteRequest"),
    level = DeprecationLevel.WARNING,
)
public data class CompleteRequest(
    val ref: Reference,
    /**
     * The argument's information
     */
    val argument: Argument,
    override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest,
    WithMeta {
    override val method: Method = Method.Defined.CompletionComplete

    @Serializable
    public data class Argument(
        /**
         * The name of the argument
         */
        val name: String,
        /**
         * The value of the argument to use for completion matching.
         */
        val value: String,
    )
}

/**
 * The server's response to a completion/complete request
 */
@Serializable
@Deprecated(
    message = "Use `CompleteResult` instead",
    replaceWith = ReplaceWith("CompleteResult", "io.modelcontextprotocol.kotlin.sdk.types.CompleteResult"),
    level = DeprecationLevel.WARNING,
)
public data class CompleteResult(val completion: Completion, override val _meta: JsonObject = EmptyJsonObject) :
    ServerResult {
    @Suppress("CanBeParameter")
    @Serializable
    public class Completion(
        /**
         * A list of completion values. Must not exceed 100 items.
         */
        public val values: List<String>,
        /**
         * The total number of completion options available. This can exceed the number of values actually sent in the response.
         */
        public val total: Int?,
        /**
         * Indicates whether there are additional completion options beyond those provided in the current response, even if the exact total is unknown.
         */
        public val hasMore: Boolean?,
    ) {
        init {
            require(values.size <= 100) {
                "'values' field must not exceed 100 items"
            }
        }
    }
}

/* Roots */

/**
 * Represents a root directory or file that the server can operate on.
 */
@Serializable
@Deprecated(
    message = "Use `Root` instead",
    replaceWith = ReplaceWith("Root", "io.modelcontextprotocol.kotlin.sdk.types.Root"),
    level = DeprecationLevel.WARNING,
)
public data class Root(
    /**
     * The URI identifying the root. This *must* start with file:// for now.
     */
    val uri: String,

    /**
     * An optional name for the root.
     */
    val name: String?,
) {
    init {
        require(uri.startsWith("file://")) {
            "'uri' param must start with 'file://'"
        }
    }
}

/**
 * Sent from the server to request a list of root URIs from the client.
 */
@Serializable
@Deprecated(
    message = "Use `ListRootsRequest` instead",
    replaceWith = ReplaceWith("ListRootsRequest", "io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest"),
    level = DeprecationLevel.WARNING,
)
public class ListRootsRequest(override val _meta: JsonObject = EmptyJsonObject) :
    ServerRequest,
    WithMeta {
    override val method: Method = Method.Defined.RootsList
}

/**
 * The client's response to a roots/list request from the server.
 */
@Serializable
@Deprecated(
    message = "Use `ListRootsResult` instead",
    replaceWith = ReplaceWith("ListRootsResult", "io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult"),
    level = DeprecationLevel.WARNING,
)
public class ListRootsResult(public val roots: List<Root>, override val _meta: JsonObject = EmptyJsonObject) :
    ClientResult

/**
 * A notification from the client to the server, informing it that the list of roots has changed.
 */
@Serializable
@Deprecated(
    message = "Use `RootsListChangedNotification` instead",
    replaceWith = ReplaceWith(
        "RootsListChangedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.RootsListChangedNotification",
    ),
    level = DeprecationLevel.WARNING,
)
public data class RootsListChangedNotification(override val params: Params = Params()) : ClientNotification {
    override val method: Method = Method.Defined.NotificationsRootsListChanged

    @Serializable
    public data class Params(override val _meta: JsonObject = EmptyJsonObject) : NotificationParams
}

/**
 * Sent from the server to create an elicitation from the client.
 */
@Serializable
@Deprecated(
    message = "Use `CreateElicitationRequest` instead",
    replaceWith = ReplaceWith(
        "CreateElicitationRequest",
        "io.modelcontextprotocol.kotlin.sdk.types.CreateElicitationRequest",
    ),
    level = DeprecationLevel.WARNING,
)
public data class CreateElicitationRequest(
    public val message: String,
    public val requestedSchema: RequestedSchema,
    override val _meta: JsonObject = EmptyJsonObject,
) : ServerRequest,
    WithMeta {
    override val method: Method = Method.Defined.ElicitationCreate

    @Serializable
    public data class RequestedSchema(
        val properties: JsonObject = EmptyJsonObject,
        val required: List<String>? = null,
    ) {
        @OptIn(ExperimentalSerializationApi::class)
        @EncodeDefault
        val type: String = "object"
    }
}

/**
 * The client's response to an elicitation/create request from the server.
 */
@Serializable
@Deprecated(
    message = "Use `CreateElicitationResult` instead",
    replaceWith = ReplaceWith(
        "CreateElicitationResult",
        "io.modelcontextprotocol.kotlin.sdk.types.CreateElicitationResult",
    ),
    level = DeprecationLevel.WARNING,
)
public data class CreateElicitationResult(
    public val action: Action,
    public val content: JsonObject? = null,
    override val _meta: JsonObject = EmptyJsonObject,
) : ClientResult {

    init {
        require(action == Action.accept || content == null) {
            "Content can only be provided for an 'accept' action"
        }
    }

    @Serializable
    public enum class Action { accept, decline, cancel }
}

/**
 * Represents an error specific to the MCP protocol.
 *
 * @property code The error code.
 * @property message The error message.
 * @property data Additional error data as a JSON object.
 */
@Deprecated(
    message = "Use `McpException` instead",
    replaceWith = ReplaceWith("McpException"),
    level = DeprecationLevel.WARNING,
)
public class McpError(public val code: Int, message: String, public val data: JsonObject = EmptyJsonObject) :
    Exception() {
    override val message: String = "MCP error $code: $message"
}
