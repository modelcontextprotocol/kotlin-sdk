@file:Suppress("FunctionName", "TooManyFunctions")

package io.modelcontextprotocol.kotlin.sdk

import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock
import io.modelcontextprotocol.kotlin.sdk.types.fromJSON
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities.Roots as TypesRoots

@Deprecated(
    message = "Use `LATEST_PROTOCOL_VERSION` instead",
    replaceWith = ReplaceWith(
        "LATEST_PROTOCOL_VERSION",
        "io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION",
    ),
    level = DeprecationLevel.ERROR,
)
public const val LATEST_PROTOCOL_VERSION: String = io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION

@Deprecated(
    message = "Use `SUPPORTED_PROTOCOL_VERSIONS` instead",
    replaceWith = ReplaceWith(
        "SUPPORTED_PROTOCOL_VERSIONS",
        "io.modelcontextprotocol.kotlin.sdk.types.SUPPORTED_PROTOCOL_VERSIONS",
    ),
    level = DeprecationLevel.ERROR,
)
public val SUPPORTED_PROTOCOL_VERSIONS: List<String> =
    io.modelcontextprotocol.kotlin.sdk.types.SUPPORTED_PROTOCOL_VERSIONS

@Deprecated(
    message = "Use `JSONRPC_VERSION` instead",
    replaceWith = ReplaceWith("JSONRPC_VERSION", "io.modelcontextprotocol.kotlin.sdk.types.JSONRPC_VERSION"),
    level = DeprecationLevel.ERROR,
)
public const val JSONRPC_VERSION: String = io.modelcontextprotocol.kotlin.sdk.types.JSONRPC_VERSION

/**
 * A progress token, used to associate progress notifications with the original request.
 * Stores message ID.
 */
@Deprecated(
    message = "Use `ProgressToken` instead",
    replaceWith = ReplaceWith("ProgressToken", "io.modelcontextprotocol.kotlin.sdk.types.ProgressToken"),
    level = DeprecationLevel.ERROR,
)
public typealias ProgressToken = io.modelcontextprotocol.kotlin.sdk.types.ProgressToken

/**
 * An opaque token used to represent a cursor for pagination.
 */
@Deprecated(
    message = "This alias will be removed. Use String directly instead.",
    replaceWith = ReplaceWith("String"),
    level = DeprecationLevel.ERROR,
)
public typealias Cursor = String

/**
 * Represents an entity that includes additional metadata in its responses.
 */
@Deprecated(
    message = "Use `WithMeta` instead",
    replaceWith = ReplaceWith("WithMeta", "io.modelcontextprotocol.kotlin.sdk.types.WithMeta"),
    level = DeprecationLevel.ERROR,
)
public typealias WithMeta = io.modelcontextprotocol.kotlin.sdk.types.WithMeta

/**
 * An implementation of [WithMeta] containing custom metadata.
 */
@Deprecated(
    message = "This class will be removed. Use `WithMeta` instead",
    replaceWith = ReplaceWith("WithMeta", "io.modelcontextprotocol.kotlin.sdk.types.WithMeta"),
    level = DeprecationLevel.ERROR,
)
public typealias CustomMeta = io.modelcontextprotocol.kotlin.sdk.types.WithMeta

/**
 * Represents a method in the protocol, which can be predefined or custom.
 */
@Deprecated(
    message = "Use `Method` instead",
    replaceWith = ReplaceWith("Method", "io.modelcontextprotocol.kotlin.sdk.types.Method"),
    level = DeprecationLevel.ERROR,
)
public sealed interface Method {
    public val value: String

    public companion object {
        /**
         * Represents a custom method defined by the user.
         */
        @Deprecated(
            message = "Use `Method.Custom` instead",
            replaceWith = ReplaceWith("Method.Custom", "io.modelcontextprotocol.kotlin.sdk.types.Method.Custom"),
            level = DeprecationLevel.ERROR,
        )
        public fun Custom(value: String): io.modelcontextprotocol.kotlin.sdk.types.Method.Custom =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Custom(value)
    }

    public object Defined {
        @Deprecated(
            message = "Use `Method.Defined.Initialize` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.Initialize",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.Initialize",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val Initialize: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.Initialize

        @Deprecated(
            message = "Use `Method.Defined.Ping` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.Ping",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.Ping",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val Ping: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.Ping

        @Deprecated(
            message = "Use `Method.Defined.ResourcesList` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.ResourcesList",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ResourcesList",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val ResourcesList: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ResourcesList

        @Deprecated(
            message = "Use `Method.Defined.ResourcesTemplatesList` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.ResourcesTemplatesList",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ResourcesTemplatesList",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val ResourcesTemplatesList: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ResourcesTemplatesList

        @Deprecated(
            message = "Use `Method.Defined.ResourcesRead` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.ResourcesRead",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ResourcesRead",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val ResourcesRead: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ResourcesRead

        @Deprecated(
            message = "Use `Method.Defined.ResourcesSubscribe` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.ResourcesSubscribe",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ResourcesSubscribe",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val ResourcesSubscribe: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ResourcesSubscribe

        @Deprecated(
            message = "Use `Method.Defined.ResourcesUnsubscribe` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.ResourcesUnsubscribe",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ResourcesUnsubscribe",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val ResourcesUnsubscribe: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ResourcesUnsubscribe

        @Deprecated(
            message = "Use `Method.Defined.PromptsList` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.PromptsList",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.PromptsList",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val PromptsList: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.PromptsList

        @Deprecated(
            message = "Use `Method.Defined.PromptsGet` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.PromptsGet",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.PromptsGet",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val PromptsGet: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.PromptsGet

        @Deprecated(
            message = "Use `Method.Defined.NotificationsCancelled` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.NotificationsCancelled",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsCancelled",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val NotificationsCancelled: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsCancelled

        @Deprecated(
            message = "Use `Method.Defined.NotificationsInitialized` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.NotificationsInitialized",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsInitialized",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val NotificationsInitialized: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsInitialized

        @Deprecated(
            message = "Use `Method.Defined.NotificationsProgress` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.NotificationsProgress",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsProgress",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val NotificationsProgress: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsProgress

        @Deprecated(
            message = "Use `Method.Defined.NotificationsMessage` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.NotificationsMessage",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsMessage",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val NotificationsMessage: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsMessage

        @Deprecated(
            message = "Use `Method.Defined.NotificationsResourcesUpdated` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.NotificationsResourcesUpdated",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsResourcesUpdated",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val NotificationsResourcesUpdated: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsResourcesUpdated

        @Deprecated(
            message = "Use `Method.Defined.NotificationsResourcesListChanged` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.NotificationsResourcesListChanged",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsResourcesListChanged",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val NotificationsResourcesListChanged: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsResourcesListChanged

        @Deprecated(
            message = "Use `Method.Defined.NotificationsToolsListChanged` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.NotificationsToolsListChanged",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsToolsListChanged",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val NotificationsToolsListChanged: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsToolsListChanged

        @Deprecated(
            message = "Use `Method.Defined.NotificationsRootsListChanged` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.NotificationsRootsListChanged",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsRootsListChanged",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val NotificationsRootsListChanged: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsRootsListChanged

        @Deprecated(
            message = "Use `Method.Defined.NotificationsPromptsListChanged` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.NotificationsPromptsListChanged",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsPromptsListChanged",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val NotificationsPromptsListChanged: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.NotificationsPromptsListChanged

        @Deprecated(
            message = "Use `Method.Defined.ToolsList` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.ToolsList",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ToolsList",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val ToolsList: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ToolsList

        @Deprecated(
            message = "Use `Method.Defined.ToolsCall` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.ToolsCall",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ToolsCall",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val ToolsCall: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ToolsCall

        @Deprecated(
            message = "Use `Method.Defined.LoggingSetLevel` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.LoggingSetLevel",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.LoggingSetLevel",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val LoggingSetLevel: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.LoggingSetLevel

        @Deprecated(
            message = "Use `Method.Defined.SamplingCreateMessage` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.SamplingCreateMessage",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.SamplingCreateMessage",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val SamplingCreateMessage: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.SamplingCreateMessage

        @Deprecated(
            message = "Use `Method.Defined.CompletionComplete` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.CompletionComplete",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.CompletionComplete",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val CompletionComplete: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.CompletionComplete

        @Deprecated(
            message = "Use `Method.Defined.RootsList` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.RootsList",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.RootsList",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val RootsList: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.RootsList

        @Deprecated(
            message = "Use `Method.Defined.ElicitationCreate` instead",
            replaceWith = ReplaceWith(
                "Method.Defined.ElicitationCreate",
                "io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ElicitationCreate",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val ElicitationCreate: io.modelcontextprotocol.kotlin.sdk.types.Method.Defined =
            io.modelcontextprotocol.kotlin.sdk.types.Method.Defined.ElicitationCreate
    }
}

/**
 * Represents a request in the protocol.
 */
@Deprecated(
    message = "Use `Request` instead",
    replaceWith = ReplaceWith("Request", "io.modelcontextprotocol.kotlin.sdk.types.Request"),
    level = DeprecationLevel.ERROR,
)
public typealias Request = io.modelcontextprotocol.kotlin.sdk.types.Request

/**
 * Converts the request to a JSON-RPC request.
 *
 * @return The JSON-RPC request representation.
 */
@Deprecated(
    message = "Use `toJSON` instead",
    replaceWith = ReplaceWith("toJSON", "io.modelcontextprotocol.kotlin.sdk.types.toJSON"),
    level = DeprecationLevel.ERROR,
)
@Suppress("DEPRECATION_ERROR")
public fun Request.toJSON(): JSONRPCRequest = this.toJSON()

/**
 * Decodes a JSON-RPC request into a protocol-specific [Request].
 *
 * @return The decoded [Request] or null
 */
@Deprecated(
    message = "Use `fromJSON` instead",
    replaceWith = ReplaceWith("fromJSON", "io.modelcontextprotocol.kotlin.sdk.types.fromJSON"),
    level = DeprecationLevel.ERROR,
)
@Suppress("DEPRECATION_ERROR")
internal fun JSONRPCRequest.fromJSON(): Request = this.fromJSON()

/**
 * A custom request with a specified method.
 */
@Deprecated(
    message = "Use `CustomRequest` instead",
    replaceWith = ReplaceWith("CustomRequest", "io.modelcontextprotocol.kotlin.sdk.types.CustomRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias CustomRequest = io.modelcontextprotocol.kotlin.sdk.types.CustomRequest

/**
 * Represents a notification in the protocol.
 */
@Deprecated(
    message = "Use `Notification` instead",
    replaceWith = ReplaceWith("Notification", "io.modelcontextprotocol.kotlin.sdk.types.Notification"),
    level = DeprecationLevel.ERROR,
)
public typealias Notification = io.modelcontextprotocol.kotlin.sdk.types.Notification

/**
 * Converts the notification to a JSON-RPC notification.
 *
 * @return The JSON-RPC notification representation.
 */
@Deprecated(
    message = "Use `toJSON` instead",
    replaceWith = ReplaceWith("toJSON", "io.modelcontextprotocol.kotlin.sdk.types.toJSON"),
    level = DeprecationLevel.ERROR,
)
@Suppress("DEPRECATION_ERROR")
public fun Notification.toJSON(): JSONRPCNotification = this.toJSON()

/**
 * Decodes a JSON-RPC notification into a protocol-specific [Notification].
 *
 * @return The decoded [Notification].
 */
@Deprecated(
    message = "Use `fromJSON` instead",
    replaceWith = ReplaceWith("toJSON", "io.modelcontextprotocol.kotlin.sdk.types.fromJSON"),
    level = DeprecationLevel.ERROR,
)
@Suppress("DEPRECATION_ERROR")
internal fun JSONRPCNotification.fromJSON(): Notification = this.fromJSON()

/**
 * Represents the result of a request, including additional metadata.
 */
@Deprecated(
    message = "Use `RequestResult` instead",
    replaceWith = ReplaceWith("RequestResult", "io.modelcontextprotocol.kotlin.sdk.types.RequestResult"),
    level = DeprecationLevel.ERROR,
)
public typealias RequestResult = io.modelcontextprotocol.kotlin.sdk.types.RequestResult

/**
 * An empty result for a request containing optional metadata.
 */
@Deprecated(
    message = "Use `EmptyResult` instead",
    replaceWith = ReplaceWith("EmptyResult", "io.modelcontextprotocol.kotlin.sdk.types.EmptyResult"),
    level = DeprecationLevel.ERROR,
)
public typealias EmptyRequestResult = io.modelcontextprotocol.kotlin.sdk.types.EmptyResult

/**
 * A uniquely identifying ID for a request in JSON-RPC.
 */
@Deprecated(
    message = "Use `RequestId` instead",
    replaceWith = ReplaceWith("RequestId", "io.modelcontextprotocol.kotlin.sdk.types.RequestId"),
    level = DeprecationLevel.ERROR,
)
public object RequestId {
    @Deprecated(
        message = "Use `RequestId.StringId` instead",
        replaceWith = ReplaceWith("RequestId.StringId", "io.modelcontextprotocol.kotlin.sdk.types.RequestId.StringId"),
        level = DeprecationLevel.ERROR,
    )
    public fun StringId(value: String): io.modelcontextprotocol.kotlin.sdk.types.RequestId.StringId =
        io.modelcontextprotocol.kotlin.sdk.types.RequestId.StringId(value)

    @Deprecated(
        message = "Use `RequestId.NumberId` instead",
        replaceWith = ReplaceWith("RequestId.NumberId", "io.modelcontextprotocol.kotlin.sdk.types.RequestId.NumberId"),
        level = DeprecationLevel.ERROR,
    )
    public fun NumberId(value: Long): io.modelcontextprotocol.kotlin.sdk.types.RequestId.NumberId =
        io.modelcontextprotocol.kotlin.sdk.types.RequestId.NumberId(value)
}

/**
 * Represents a JSON-RPC message in the protocol.
 */
@Deprecated(
    message = "Use `JSONRPCMessage` instead",
    replaceWith = ReplaceWith("JSONRPCMessage", "io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage"),
    level = DeprecationLevel.ERROR,
)
public typealias JSONRPCMessage = io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage

/**
 * A request that expects a response.
 */
@Deprecated(
    message = "Use `JSONRPCRequest` instead",
    replaceWith = ReplaceWith("JSONRPCRequest", "io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias JSONRPCRequest = io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest

/**
 * A notification which does not expect a response.
 */
@Deprecated(
    message = "Use `JSONRPCNotification` instead",
    replaceWith = ReplaceWith("JSONRPCNotification", "io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification"),
    level = DeprecationLevel.ERROR,
)
public typealias JSONRPCNotification = io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification

/**
 * A successful (non-error) response to a request.
 */
@Deprecated(
    message = "Use `JSONRPCResponse` instead",
    replaceWith = ReplaceWith("JSONRPCResponse", "io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse"),
    level = DeprecationLevel.ERROR,
)
public typealias JSONRPCResponse = io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse

/**
 * An incomplete set of error codes that may appear in JSON-RPC responses.
 */
@Deprecated(
    message = "Use `RPCError` instead",
    replaceWith = ReplaceWith("RPCError", "io.modelcontextprotocol.kotlin.sdk.types.RPCError"),
    level = DeprecationLevel.ERROR,
)
public sealed interface ErrorCode {
    public val code: Int

    public object Defined {
        @Deprecated(
            message = "Use `RPCError.ErrorCode.CONNECTION_CLOSED` instead",
            replaceWith = ReplaceWith(
                "RPCError.ErrorCode.CONNECTION_CLOSED",
                "io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.CONNECTION_CLOSED",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val ConnectionClosed: Int = io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.CONNECTION_CLOSED

        @Deprecated(
            message = "Use `RPCError.ErrorCode.REQUEST_TIMEOUT` instead",
            replaceWith = ReplaceWith(
                "RPCError.ErrorCode.REQUEST_TIMEOUT",
                "io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.REQUEST_TIMEOUT",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val RequestTimeout: Int = io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.REQUEST_TIMEOUT

        @Deprecated(
            message = "Use `RPCError.ErrorCode.PARSE_ERROR` instead",
            replaceWith = ReplaceWith(
                "RPCError.ErrorCode.PARSE_ERROR",
                "io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.PARSE_ERROR",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val ParseError: Int = io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.PARSE_ERROR

        @Deprecated(
            message = "Use `RPCError.ErrorCode.INVALID_REQUEST` instead",
            replaceWith = ReplaceWith(
                "RPCError.ErrorCode.INVALID_REQUEST",
                "io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.INVALID_REQUEST",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val InvalidRequest: Int = io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.INVALID_REQUEST

        @Deprecated(
            message = "Use `RPCError.ErrorCode.METHOD_NOT_FOUND` instead",
            replaceWith = ReplaceWith(
                "RPCError.ErrorCode.METHOD_NOT_FOUND",
                "io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.METHOD_NOT_FOUND",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val MethodNotFound: Int = io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.METHOD_NOT_FOUND

        @Deprecated(
            message = "Use `RPCError.ErrorCode.INVALID_PARAMS` instead",
            replaceWith = ReplaceWith(
                "RPCError.ErrorCode.INVALID_PARAMS",
                "io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.INVALID_PARAMS",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val InvalidParams: Int = io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.INVALID_PARAMS

        @Deprecated(
            message = "Use `RPCError.ErrorCode.INTERNAL_ERROR` instead",
            replaceWith = ReplaceWith(
                "RPCError.ErrorCode.INTERNAL_ERROR",
                "io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.INTERNAL_ERROR",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val InternalError: Int = io.modelcontextprotocol.kotlin.sdk.types.RPCError.ErrorCode.INTERNAL_ERROR
    }

    @Deprecated(
        message = "Use `RPCError` instead",
        replaceWith = ReplaceWith("RPCError", "io.modelcontextprotocol.kotlin.sdk.types.RPCError"),
        level = DeprecationLevel.ERROR,
    )
    @Serializable
    @Suppress("DEPRECATION_ERROR")
    public data class Unknown(override val code: Int) : ErrorCode
}

/**
 * A response to a request that indicates an error occurred.
 */
@Deprecated(
    message = "Use `JSONRPCError` instead",
    replaceWith = ReplaceWith("JSONRPCError", "io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError"),
    level = DeprecationLevel.ERROR,
)
public typealias JSONRPCError = io.modelcontextprotocol.kotlin.sdk.types.JSONRPCError

/**
 * Base interface for notification parameters with optional metadata.
 */
@Deprecated(
    message = "Use `NotificationParams` instead",
    replaceWith = ReplaceWith("NotificationParams", "io.modelcontextprotocol.kotlin.sdk.types.NotificationParams"),
    level = DeprecationLevel.ERROR,
)
public typealias NotificationParams = io.modelcontextprotocol.kotlin.sdk.types.NotificationParams

/* Cancellation */

@Deprecated(
    message = "Use `CancelledNotification` instead",
    replaceWith = ReplaceWith(
        "CancelledNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.CancelledNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public object CancelledNotification {
    @Deprecated(
        message = "Use `CancelledNotificationParams` instead",
        replaceWith = ReplaceWith(
            "CancelledNotificationParams",
            "io.modelcontextprotocol.kotlin.sdk.types.CancelledNotificationParams",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun Params(
        requestId: io.modelcontextprotocol.kotlin.sdk.types.RequestId,
        reason: String? = null,
        meta: JsonObject? = null,
    ): io.modelcontextprotocol.kotlin.sdk.types.CancelledNotificationParams =
        io.modelcontextprotocol.kotlin.sdk.types.CancelledNotificationParams(requestId, reason, meta)
}

/**
 * This notification can be sent by either side to indicate that it is cancelling a previously issued request.
 *
 * The request SHOULD still be in-flight, but due to communication latency, it is always possible that
 * this notification MAY arrive after the request has already finished.
 *
 * This notification indicates that the result will be unused, so any associated processing SHOULD cease.
 *
 * A client MUST NOT attempt to cancel its `initialize` request.
 */
@Deprecated(
    message = "Use `CancelledNotification` instead",
    replaceWith = ReplaceWith(
        "CancelledNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.CancelledNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public fun CancelledNotification(
    params: io.modelcontextprotocol.kotlin.sdk.types.CancelledNotificationParams,
): io.modelcontextprotocol.kotlin.sdk.types.CancelledNotification =
    io.modelcontextprotocol.kotlin.sdk.types.CancelledNotification(params)

/* Initialization */

/**
 * Describes the name and version of an MCP implementation.
 */
@Deprecated(
    message = "Use `Implementation` instead",
    replaceWith = ReplaceWith("Implementation", "io.modelcontextprotocol.kotlin.sdk.types.Implementation"),
    level = DeprecationLevel.ERROR,
)
public typealias Implementation = io.modelcontextprotocol.kotlin.sdk.types.Implementation

@Deprecated(
    message = "Use `ClientCapabilities` instead",
    replaceWith = ReplaceWith("ClientCapabilities", "io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities"),
    level = DeprecationLevel.ERROR,
)
public object ClientCapabilities {
    /**
     * Creates an instance of [Roots], representing the client's capability
     * to list roots and optionally listen for changes in the roots list.
     *
     * @param listChanged Optional flag indicating whether the client supports notifications
     * for changes to the roots list. If set to true, the client will notify the server when
     * roots are added or removed.
     * @return A new instance of [Roots] configured with the provided [listChanged] value.
     */
    @Deprecated(
        message = "Use `ClientCapabilities.Roots` instead",
        replaceWith = ReplaceWith(
            "ClientCapabilities.Roots",
            "io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities.Roots",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun Roots(listChanged: Boolean? = null): TypesRoots = TypesRoots(listChanged)
}

/**
 * Capabilities a client may support.
 * Known capabilities are defined here, in this, but this is not a closed set:
 * any client can define its own, additional capabilities.
 */
@Deprecated(
    message = "Use `ClientCapabilities` instead",
    replaceWith = ReplaceWith("ClientCapabilities", "io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities"),
    level = DeprecationLevel.ERROR,
)
public fun ClientCapabilities(
    sampling: JsonObject? = null,
    roots: TypesRoots? = null,
    elicitation: JsonObject? = null,
    experimental: JsonObject? = null,
): io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities =
    io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities(sampling, roots, elicitation, experimental)

/**
 * Represents a request sent by the client.
 */
@Deprecated(
    message = "Use `ClientRequest` instead",
    replaceWith = ReplaceWith("ClientRequest", "io.modelcontextprotocol.kotlin.sdk.types.ClientRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias ClientRequest = io.modelcontextprotocol.kotlin.sdk.types.ClientRequest

/**
 * Represents a notification sent by the client.
 */
@Deprecated(
    message = "Use `ClientNotification` instead",
    replaceWith = ReplaceWith("ClientNotification", "io.modelcontextprotocol.kotlin.sdk.types.ClientNotification"),
    level = DeprecationLevel.ERROR,
)
public typealias ClientNotification = io.modelcontextprotocol.kotlin.sdk.types.ClientNotification

/**
 * Represents a result returned to the client.
 */
@Deprecated(
    message = "Use `ClientResult` instead",
    replaceWith = ReplaceWith("ClientResult", "io.modelcontextprotocol.kotlin.sdk.types.ClientResult"),
    level = DeprecationLevel.ERROR,
)
public typealias ClientResult = io.modelcontextprotocol.kotlin.sdk.types.ClientResult

/**
 * Represents a request sent by the server.
 */
@Deprecated(
    message = "Use `ServerRequest` instead",
    replaceWith = ReplaceWith("ServerRequest", "io.modelcontextprotocol.kotlin.sdk.types.ServerRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias ServerRequest = io.modelcontextprotocol.kotlin.sdk.types.ServerRequest

/**
 * Represents a notification sent by the server.
 */
@Deprecated(
    message = "Use `ServerNotification` instead",
    replaceWith = ReplaceWith("ServerNotification", "io.modelcontextprotocol.kotlin.sdk.types.ServerNotification"),
    level = DeprecationLevel.ERROR,
)
public typealias ServerNotification = io.modelcontextprotocol.kotlin.sdk.types.ServerNotification

/**
 * Represents a result returned by the server.
 */
@Deprecated(
    message = "Use `ServerResult` instead",
    replaceWith = ReplaceWith("ServerResult", "io.modelcontextprotocol.kotlin.sdk.types.ServerResult"),
    level = DeprecationLevel.ERROR,
)
public typealias ServerResult = io.modelcontextprotocol.kotlin.sdk.types.ServerResult

/**
 * Represents a request or notification for an unknown method.
 *
 * @param method The method that is unknown.
 */
@Serializable
@Deprecated(
    message = "This class will be removed",
    level = DeprecationLevel.ERROR,
)
@Suppress("DEPRECATION_ERROR")
public data class UnknownMethodRequestOrNotification(val method: Method, val params: NotificationParams? = null)

/**
 * This request is sent from the client to the server when it first connects, asking it to begin initialization.
 */
@Deprecated(
    message = "Use `InitializeRequest` instead",
    replaceWith = ReplaceWith("InitializeRequest", "io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias InitializeRequest = io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest

@Deprecated(
    message = "Use `ServerCapabilities` instead",
    replaceWith = ReplaceWith("ServerCapabilities", "io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities"),
    level = DeprecationLevel.ERROR,
)
public object ServerCapabilities {

    @Deprecated(
        message = "Use `ServerCapabilities.Prompts` instead",
        replaceWith = ReplaceWith(
            "ServerCapabilities.Prompts",
            "io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Prompts",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun Prompts(
        listChanged: Boolean? = null,
    ): io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Prompts =
        io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Prompts(listChanged)

    @Deprecated(
        message = "Use `ServerCapabilities.Resources` instead",
        replaceWith = ReplaceWith(
            "ServerCapabilities.Resources",
            "io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Resources",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun Resources(
        listChanged: Boolean? = null,
        subscribe: Boolean? = null,
    ): io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Resources =
        io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Resources(listChanged, subscribe)

    @Deprecated(
        message = "Use `ServerCapabilities.Tools` instead",
        replaceWith = ReplaceWith(
            "ServerCapabilities.Tools",
            "io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Tools",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun Tools(listChanged: Boolean? = null): io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Tools =
        io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Tools(listChanged)
}

/**
 * Represents the capabilities that a server can support.
 *
 * @property experimental Experimental, non-standard capabilities that the server supports.
 * @property logging Present if the server supports sending log messages to the client.
 * @property prompts Capabilities related to prompt templates offered by the server.
 * @property resources Capabilities related to resources available on the server.
 * @property tools Capabilities related to tools that can be called on the server.
 */
@Deprecated(
    message = "Use `ServerCapabilities` instead",
    replaceWith = ReplaceWith("ServerCapabilities", "io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities"),
    level = DeprecationLevel.ERROR,
)
@Suppress("LongParameterList")
public fun ServerCapabilities(
    tools: io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Tools? = null,
    resources: io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Resources? = null,
    prompts: io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities.Prompts? = null,
    logging: JsonObject? = null,
    completions: JsonObject? = null,
    experimental: JsonObject? = null,
): io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities =
    io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities(
        tools,
        resources,
        prompts,
        logging,
        completions,
        experimental,
    )

/**
 * After receiving an initialized request from the client, the server sends this response.
 */
@Deprecated(
    message = "Use `InitializeResult` instead",
    replaceWith = ReplaceWith("InitializeResult", "io.modelcontextprotocol.kotlin.sdk.types.InitializeResult"),
    level = DeprecationLevel.ERROR,
)
public typealias InitializeResult = io.modelcontextprotocol.kotlin.sdk.types.InitializeResult

@Deprecated(
    message = "Use `InitializedNotification` instead",
    replaceWith = ReplaceWith(
        "InitializedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public object InitializedNotification {
    @Deprecated(
        message = "Use `BaseNotificationParams` instead",
        replaceWith = ReplaceWith(
            "BaseNotificationParams",
            "io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams",
        ),
        level = DeprecationLevel.ERROR,
    )
    @Suppress("DEPRECATION_ERROR")
    public fun Params(
        meta: JsonObject = EmptyJsonObject,
    ): io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams =
        io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams(meta)
}

/**
 * This notification is sent from the client to the server after initialization has finished.
 */
@Deprecated(
    message = "Use `InitializedNotification` instead",
    replaceWith = ReplaceWith(
        "InitializedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public fun InitializedNotification(
    params: io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams? = null,
): io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification =
    io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification(params)

/* Ping */

/**
 * A ping, issued by either the server or the client, to check that the other party is still alive.
 * The receiver must promptly respond, or else it may be disconnected.
 */
@Deprecated(
    message = "Use `PingRequest` instead",
    replaceWith = ReplaceWith("PingRequest", "io.modelcontextprotocol.kotlin.sdk.types.PingRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias PingRequest = io.modelcontextprotocol.kotlin.sdk.types.PingRequest

/**
 * Represents the base interface for progress tracking.
 */
@Serializable
@Deprecated(
    message = "This interface will be removed",
    level = DeprecationLevel.ERROR,
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
 */
@Deprecated(
    message = "Use `Progress` instead",
    replaceWith = ReplaceWith("Progress", "io.modelcontextprotocol.kotlin.sdk.types.Progress"),
    level = DeprecationLevel.ERROR,
)
public typealias Progress = io.modelcontextprotocol.kotlin.sdk.types.Progress

@Deprecated(
    message = "Use `ProgressNotification` instead",
    replaceWith = ReplaceWith("ProgressNotification", "io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification"),
    level = DeprecationLevel.ERROR,
)
public object ProgressNotification {
    @Deprecated(
        message = "Use `ProgressNotificationParams` instead",
        replaceWith = ReplaceWith(
            "ProgressNotificationParams",
            "io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun Params(
        progressToken: io.modelcontextprotocol.kotlin.sdk.types.ProgressToken,
        progress: Double,
        total: Double? = null,
        message: String? = null,
        meta: JsonObject? = null,
    ): io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams =
        io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams(
            progressToken,
            progress,
            total,
            message,
            meta,
        )
}

/**
 * An out-of-band notification used to inform the receiver of a progress update for a long-running request.
 */
@Deprecated(
    message = "Use `ProgressNotification` instead",
    replaceWith = ReplaceWith("ProgressNotification", "io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification"),
    level = DeprecationLevel.ERROR,
)
public fun ProgressNotification(
    params: io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams,
): io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification =
    io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification(params)

/* Pagination */

/**
 * Represents a request supporting pagination.
 */
@Deprecated(
    message = "Use `PaginatedRequest` instead",
    replaceWith = ReplaceWith("PaginatedRequest", "io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias PaginatedRequest = io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequest

/**
 * Represents a paginated result of a request.
 */
@Deprecated(
    message = "Use `PaginatedResult` instead",
    replaceWith = ReplaceWith("PaginatedResult", "io.modelcontextprotocol.kotlin.sdk.types.PaginatedResult"),
    level = DeprecationLevel.ERROR,
)
public typealias PaginatedResult = io.modelcontextprotocol.kotlin.sdk.types.PaginatedResult

/* Resources */

/**
 * The contents of a specific resource or sub-resource.
 */
@Deprecated(
    message = "Use `ResourceContents` instead",
    replaceWith = ReplaceWith("ResourceContents", "io.modelcontextprotocol.kotlin.sdk.types.ResourceContents"),
    level = DeprecationLevel.ERROR,
)
public typealias ResourceContents = io.modelcontextprotocol.kotlin.sdk.types.ResourceContents

/**
 * Represents the text contents of a resource.
 */
@Deprecated(
    message = "Use `TextResourceContents` instead",
    replaceWith = ReplaceWith("TextResourceContents", "io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents"),
    level = DeprecationLevel.ERROR,
)
public typealias TextResourceContents = io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents

/**
 * Represents the binary contents of a resource encoded as a base64 string.
 */
@Deprecated(
    message = "Use `BlobResourceContents` instead",
    replaceWith = ReplaceWith("BlobResourceContents", "io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents"),
    level = DeprecationLevel.ERROR,
)
public typealias BlobResourceContents = io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents

/**
 * Represents resource contents with unknown or unspecified data.
 */
@Deprecated(
    message = "Use `UnknownResourceContents` instead",
    replaceWith = ReplaceWith(
        "UnknownResourceContents",
        "io.modelcontextprotocol.kotlin.sdk.types.UnknownResourceContents",
    ),
    level = DeprecationLevel.ERROR,
)
public typealias UnknownResourceContents = io.modelcontextprotocol.kotlin.sdk.types.UnknownResourceContents

/**
 * A known resource that the server is capable of reading.
 */
@Deprecated(
    message = "Use `Resource` instead",
    replaceWith = ReplaceWith("Resource", "io.modelcontextprotocol.kotlin.sdk.types.Resource"),
    level = DeprecationLevel.ERROR,
)
public typealias Resource = io.modelcontextprotocol.kotlin.sdk.types.Resource

/**
 * A template description for resources available on the server.
 */
@Deprecated(
    message = "Use `ResourceTemplate` instead",
    replaceWith = ReplaceWith("ResourceTemplate", "io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate"),
    level = DeprecationLevel.ERROR,
)
public typealias ResourceTemplate = io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplate

/**
 * Sent from the client to request a list of resources the server has.
 */
@Deprecated(
    message = "Use `ListResourcesRequest` instead",
    replaceWith = ReplaceWith("ListResourcesRequest", "io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias ListResourcesRequest = io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest

/**
 * The server's response to a resources/list request from the client.
 */
@Deprecated(
    message = "Use `ListResourcesResult` instead",
    replaceWith = ReplaceWith("ListResourcesResult", "io.modelcontextprotocol.kotlin.sdk.types.ListResourcesResult"),
    level = DeprecationLevel.ERROR,
)
public typealias ListResourcesResult = io.modelcontextprotocol.kotlin.sdk.types.ListResourcesResult

/**
 * Sent from the client to request a list of resource templates the server has.
 */
@Deprecated(
    message = "Use `ListResourceTemplatesRequest` instead",
    replaceWith = ReplaceWith(
        "ListResourceTemplatesRequest",
        "io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesRequest",
    ),
    level = DeprecationLevel.ERROR,
)
public typealias ListResourceTemplatesRequest = io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesRequest

/**
 * The server's response to a resources/templates/list request from the client.
 */
@Deprecated(
    message = "Use `ListResourceTemplatesResult` instead",
    replaceWith = ReplaceWith(
        "ListResourceTemplatesResult",
        "io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesResult",
    ),
    level = DeprecationLevel.ERROR,
)
public typealias ListResourceTemplatesResult = io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesResult

/**
 * Sent from the client to the server to read a specific resource URI.
 */
@Deprecated(
    message = "Use `ReadResourceRequest` instead",
    replaceWith = ReplaceWith("ReadResourceRequest", "io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias ReadResourceRequest = io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest

/**
 * The server's response to a resources/read request from the client.
 */
@Deprecated(
    message = "Use `ReadResourceResult` instead",
    replaceWith = ReplaceWith("ReadResourceResult", "io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult"),
    level = DeprecationLevel.ERROR,
)
public typealias ReadResourceResult = io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult

@Deprecated(
    message = "Use `ResourceListChangedNotification` instead",
    replaceWith = ReplaceWith(
        "ResourceListChangedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public object ResourceListChangedNotification {
    @Deprecated(
        message = "Use `BaseNotificationParams` instead",
        replaceWith = ReplaceWith(
            "BaseNotificationParams",
            "io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun Params(meta: JsonObject? = null): io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams =
        io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams(meta)
}

/**
 * An optional notification from the server to the client,
 * informing it that the list of resources it can read from has changed.
 * Servers may issue this without any previous subscription from the client.
 */
@Deprecated(
    message = "Use `ResourceListChangedNotification` instead",
    replaceWith = ReplaceWith(
        "ResourceListChangedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public fun ResourceListChangedNotification(
    params: io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams? = null,
): io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification =
    io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification(params)

/**
 * Sent from the client to request resources/updated notifications from the server
 * whenever a particular resource changes.
 */
@Deprecated(
    message = "Use `SubscribeRequest` instead",
    replaceWith = ReplaceWith("SubscribeRequest", "io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias SubscribeRequest = io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequest

/**
 * Sent from the client to request cancellation of resources/updated notifications from the server.
 * This should follow a previous resources/subscribe request.
 */
@Deprecated(
    message = "Use `UnsubscribeRequest` instead",
    replaceWith = ReplaceWith("UnsubscribeRequest", "io.modelcontextprotocol.kotlin.sdk.types.UnsubscribeRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias UnsubscribeRequest = io.modelcontextprotocol.kotlin.sdk.types.UnsubscribeRequest

@Deprecated(
    message = "Use `ResourceUpdatedNotification` instead",
    replaceWith = ReplaceWith(
        "ResourceUpdatedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public object ResourceUpdatedNotification {
    @Deprecated(
        message = "Use `ResourceUpdatedNotificationParams` instead",
        replaceWith = ReplaceWith(
            "ResourceUpdatedNotificationParams",
            "io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotificationParams",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun Params(
        uri: String,
        meta: JsonObject? = null,
    ): io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotificationParams =
        io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotificationParams(uri, meta)
}

/**
 * A notification from the server to the client, informing it that a resource has changed and may need to be read again.
 * This should only be sent if the client previously sent a resources/subscribe request.
 */
@Deprecated(
    message = "Use `ResourceUpdatedNotification` instead",
    replaceWith = ReplaceWith(
        "ResourceUpdatedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public fun ResourceUpdatedNotification(
    params: io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotificationParams,
): io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification =
    io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification(params)

/* Prompts */

/**
 * Describes an argument that a prompt can accept.
 */
@Deprecated(
    message = "Use `PromptArgument` instead",
    replaceWith = ReplaceWith("PromptArgument", "io.modelcontextprotocol.kotlin.sdk.types.PromptArgument"),
    level = DeprecationLevel.ERROR,
)
public typealias PromptArgument = io.modelcontextprotocol.kotlin.sdk.types.PromptArgument

/**
 * A prompt or prompt template that the server offers.
 */
@Deprecated(
    message = "Use `Prompt` instead",
    replaceWith = ReplaceWith("Prompt", "io.modelcontextprotocol.kotlin.sdk.types.Prompt"),
    level = DeprecationLevel.ERROR,
)
public typealias Prompt = io.modelcontextprotocol.kotlin.sdk.types.Prompt

/**
 * Sent from the client to request a list of prompts and prompt templates the server has.
 */
@Deprecated(
    message = "Use `ListPromptsRequest` instead",
    replaceWith = ReplaceWith("ListPromptsRequest", "io.modelcontextprotocol.kotlin.sdk.types.ListPromptsRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias ListPromptsRequest = io.modelcontextprotocol.kotlin.sdk.types.ListPromptsRequest

/**
 * The server's response to a prompts/list request from the client.
 */
@Deprecated(
    message = "Use `ListPromptsResult` instead",
    replaceWith = ReplaceWith("ListPromptsResult", "io.modelcontextprotocol.kotlin.sdk.types.ListPromptsResult"),
    level = DeprecationLevel.ERROR,
)
public typealias ListPromptsResult = io.modelcontextprotocol.kotlin.sdk.types.ListPromptsResult

/**
 * Used by the client to get a prompt provided by the server.
 */
@Deprecated(
    message = "Use `GetPromptRequest` instead",
    replaceWith = ReplaceWith("GetPromptRequest", "io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias GetPromptRequest = io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest

/**
 * Represents the content of a prompt message.
 */
@Deprecated(
    message = "Use `ContentBlock` instead",
    replaceWith = ReplaceWith("ContentBlock"),
    level = DeprecationLevel.ERROR,
)
public typealias PromptMessageContent = ContentBlock

/**
 * Represents prompt message content that is either text, image or audio.
 */
@Deprecated(
    message = "Use `MediaContent` instead",
    replaceWith = ReplaceWith("MediaContent"),
    level = DeprecationLevel.ERROR,
)
public typealias PromptMessageContentMultimodal = io.modelcontextprotocol.kotlin.sdk.types.MediaContent

/**
 * Text provided to or from an LLM.
 */
@Deprecated(
    message = "Use `TextContent` instead",
    replaceWith = ReplaceWith("TextContent", "io.modelcontextprotocol.kotlin.sdk.types.TextContent"),
    level = DeprecationLevel.ERROR,
)
public typealias TextContent = io.modelcontextprotocol.kotlin.sdk.types.TextContent

/**
 * An image provided to or from an LLM.
 */
@Deprecated(
    message = "Use `ImageContent` instead",
    replaceWith = ReplaceWith("ImageContent", "io.modelcontextprotocol.kotlin.sdk.types.ImageContent"),
    level = DeprecationLevel.ERROR,
)
public typealias ImageContent = io.modelcontextprotocol.kotlin.sdk.types.ImageContent

/**
 * Audio provided to or from an LLM.
 */
@Deprecated(
    message = "Use `AudioContent` instead",
    replaceWith = ReplaceWith("AudioContent", "io.modelcontextprotocol.kotlin.sdk.types.AudioContent"),
    level = DeprecationLevel.ERROR,
)
public typealias AudioContent = io.modelcontextprotocol.kotlin.sdk.types.AudioContent

/**
 * Unknown content provided to or from an LLM.
 */
@Serializable
@Deprecated(
    message = "This class will be removed.",
    replaceWith = ReplaceWith("MediaContent"),
    level = DeprecationLevel.ERROR,
)
public data class UnknownContent(val type: String)

/**
 * The contents of a resource, embedded into a prompt or tool call result.
 */
@Deprecated(
    message = "Use `EmbeddedResource` instead",
    replaceWith = ReplaceWith("EmbeddedResource", "io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource"),
    level = DeprecationLevel.ERROR,
)
public typealias EmbeddedResource = io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource

/**
 * Enum representing the role of a participant.
 */
@Deprecated(
    message = "Use `Role` instead",
    replaceWith = ReplaceWith("Role", "io.modelcontextprotocol.kotlin.sdk.types.Role"),
    level = DeprecationLevel.ERROR,
)
public object Role {
    @Deprecated(
        message = "Use `Role.User` instead",
        replaceWith = ReplaceWith("Role.User", "io.modelcontextprotocol.kotlin.sdk.types.Role"),
        level = DeprecationLevel.ERROR,
    )
    public val user: io.modelcontextprotocol.kotlin.sdk.types.Role = io.modelcontextprotocol.kotlin.sdk.types.Role.User

    @Deprecated(
        message = "Use `Role.Assistant` instead",
        replaceWith = ReplaceWith("Role.Assistant", "io.modelcontextprotocol.kotlin.sdk.types.Role"),
        level = DeprecationLevel.ERROR,
    )
    public val assistant: io.modelcontextprotocol.kotlin.sdk.types.Role =
        io.modelcontextprotocol.kotlin.sdk.types.Role.Assistant
}

/**
 * Optional annotations for the client.
 * The client can use annotations to inform how objects are used or displayed.
 */
@Deprecated(
    message = "Use `Annotations` instead",
    replaceWith = ReplaceWith("Annotations", "io.modelcontextprotocol.kotlin.sdk.types.Annotations"),
    level = DeprecationLevel.ERROR,
)
public typealias Annotations = io.modelcontextprotocol.kotlin.sdk.types.Annotations

/**
 * Describes a message returned as part of a prompt.
 */
@Deprecated(
    message = "Use `PromptMessage` instead",
    replaceWith = ReplaceWith("PromptMessage", "io.modelcontextprotocol.kotlin.sdk.types.PromptMessage"),
    level = DeprecationLevel.ERROR,
)
public typealias PromptMessage = io.modelcontextprotocol.kotlin.sdk.types.PromptMessage

/**
 * The server's response to a prompts/get request from the client.
 */
@Deprecated(
    message = "Use `GetPromptResult` instead",
    replaceWith = ReplaceWith("GetPromptResult", "io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult"),
    level = DeprecationLevel.ERROR,
)
public typealias GetPromptResult = io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult

@Deprecated(
    message = "Use `PromptListChangedNotification` instead",
    replaceWith = ReplaceWith(
        "PromptListChangedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public object PromptListChangedNotification {
    @Deprecated(
        message = "Use `BaseNotificationParams` instead",
        replaceWith = ReplaceWith(
            "BaseNotificationParams",
            "io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun Params(meta: JsonObject? = null): io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams =
        io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams(meta)
}

/**
 * An optional notification from the server to the client, informing it that the list of prompts it offers has changed.
 * Servers may issue this without any previous subscription from the client.
 */
@Deprecated(
    message = "Use `PromptListChangedNotification` instead",
    replaceWith = ReplaceWith(
        "PromptListChangedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public fun PromptListChangedNotification(
    params: io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams,
): io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification =
    io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification(params)

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
@Deprecated(
    message = "Use `ToolAnnotations` instead",
    replaceWith = ReplaceWith("ToolAnnotations", "io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations"),
    level = DeprecationLevel.ERROR,
)
public typealias ToolAnnotations = io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations

@Deprecated(
    message = "Use `Tool` instead",
    replaceWith = ReplaceWith("Tool", "io.modelcontextprotocol.kotlin.sdk.types.Tool"),
    level = DeprecationLevel.ERROR,
)
public object Tool {
    @Deprecated(
        message = "Use `ToolSchema` instead",
        replaceWith = ReplaceWith("ToolSchema", "io.modelcontextprotocol.kotlin.sdk.types.ToolSchema"),
        level = DeprecationLevel.ERROR,
    )
    public fun Input(
        properties: JsonObject? = null,
        required: List<String>? = null,
    ): io.modelcontextprotocol.kotlin.sdk.types.ToolSchema =
        io.modelcontextprotocol.kotlin.sdk.types.ToolSchema(properties, required)

    @Deprecated(
        message = "Use `ToolSchema` instead",
        replaceWith = ReplaceWith("ToolSchema", "io.modelcontextprotocol.kotlin.sdk.types.ToolSchema"),
        level = DeprecationLevel.ERROR,
    )
    public fun Output(
        properties: JsonObject? = null,
        required: List<String>? = null,
    ): io.modelcontextprotocol.kotlin.sdk.types.ToolSchema =
        io.modelcontextprotocol.kotlin.sdk.types.ToolSchema(properties, required)
}

/**
 * Definition for a tool the client can call.
 */
@Deprecated(
    message = "Use `Tool` instead",
    replaceWith = ReplaceWith("Tool", "io.modelcontextprotocol.kotlin.sdk.types.Tool"),
    level = DeprecationLevel.ERROR,
)
@Suppress("LongParameterList", "FunctionParameterNaming")
public fun Tool(
    name: String,
    inputSchema: io.modelcontextprotocol.kotlin.sdk.types.ToolSchema,
    description: String? = null,
    outputSchema: io.modelcontextprotocol.kotlin.sdk.types.ToolSchema? = null,
    title: String? = null,
    annotations: io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations? = null,
    icons: List<io.modelcontextprotocol.kotlin.sdk.types.Icon>? = null,
    _meta: JsonObject? = null,
): io.modelcontextprotocol.kotlin.sdk.types.Tool = io.modelcontextprotocol.kotlin.sdk.types.Tool(
    name,
    inputSchema,
    description,
    outputSchema,
    title,
    annotations,
    icons,
    _meta,
)

/**
 * Sent from the client to request a list of tools the server has.
 */
@Deprecated(
    message = "Use `ListToolsRequest` instead",
    replaceWith = ReplaceWith("ListToolsRequest", "io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias ListToolsRequest = io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest

/**
 * The server's response to a tools/list request from the client.
 */
@Deprecated(
    message = "Use `ListToolsResult` instead",
    replaceWith = ReplaceWith("ListToolsResult", "io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult"),
    level = DeprecationLevel.ERROR,
)
public typealias ListToolsResult = io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult

/**
 * The server's response to a tool call.
 */
@Serializable
@Deprecated(
    message = "This interface will be removed.",
    replaceWith = ReplaceWith("CallToolResult", "io.modelcontextprotocol.kotlin.sdk.types.CallToolResult"),
    level = DeprecationLevel.ERROR,
)
@Suppress("DEPRECATION_ERROR")
public sealed interface CallToolResultBase {
    public val content: List<PromptMessageContent>
    public val structuredContent: JsonObject?
    public val isError: Boolean? get() = false
}

@Deprecated(
    message = "Use `CallToolResult` instead",
    replaceWith = ReplaceWith("CallToolResult", "io.modelcontextprotocol.kotlin.sdk.types.CallToolResult"),
    level = DeprecationLevel.ERROR,
)
public object CallToolResult

/**
 * The server's response to a tool call.
 */
@Deprecated(
    message = "Use `CallToolResult` instead",
    replaceWith = ReplaceWith("CallToolResult", "io.modelcontextprotocol.kotlin.sdk.types.CallToolResult"),
    level = DeprecationLevel.ERROR,
)
public fun CallToolResult(
    content: List<ContentBlock>,
    isError: Boolean? = null,
    structuredContent: JsonObject? = null,
    meta: JsonObject? = null,
): io.modelcontextprotocol.kotlin.sdk.types.CallToolResult =
    io.modelcontextprotocol.kotlin.sdk.types.CallToolResult(content, isError, structuredContent, meta)

/**
 * [CallToolResult] extended with backwards compatibility to protocol version 2024-11-05.
 */
@Serializable
@Deprecated(
    message = "This class will be removed.",
    replaceWith = ReplaceWith("ToolCallResult"),
    level = DeprecationLevel.ERROR,
)
@Suppress("DEPRECATION_ERROR", "ConstructorParameterNaming")
public class CompatibilityCallToolResult(
    public val content: List<PromptMessageContent>,
    public val structuredContent: JsonObject? = null,
    public val isError: Boolean? = false,
    public val _meta: JsonObject = EmptyJsonObject,
    public val toolResult: JsonObject = EmptyJsonObject,
)

/**
 * Used by the client to invoke a tool provided by the server.
 */
@Deprecated(
    message = "Use `CallToolRequest` instead",
    replaceWith = ReplaceWith("CallToolRequest", "io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias CallToolRequest = io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest

@Deprecated(
    message = "Use `ToolListChangedNotification` instead",
    replaceWith = ReplaceWith(
        "ToolListChangedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public object ToolListChangedNotification {
    @Deprecated(
        message = "Use `BaseNotificationParams` instead",
        replaceWith = ReplaceWith(
            "BaseNotificationParams",
            "io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun Params(meta: JsonObject? = null): io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams =
        io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams(meta)
}

/**
 * An optional notification from the server to the client, informing it that the list of tools it offers has changed.
 * Servers may issue this without any previous subscription from the client.
 */
@Deprecated(
    message = "Use `ToolListChangedNotification` instead",
    replaceWith = ReplaceWith(
        "ToolListChangedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public fun ToolListChangedNotification(
    params: io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams,
): io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification =
    io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification(params)

/* Logging */

/**
 * The severity of a log message.
 */
@Deprecated(
    message = "Use `LoggingLevel` instead",
    replaceWith = ReplaceWith("LoggingLevel", "io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel"),
    level = DeprecationLevel.ERROR,
)
public object LoggingLevel {
    @Deprecated(
        message = "Use `LoggingLevel.Debug` instead",
        replaceWith = ReplaceWith("LoggingLevel.Debug", "io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel"),
        level = DeprecationLevel.ERROR,
    )
    public val debug: io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel =
        io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel.Debug

    @Deprecated(
        message = "Use `LoggingLevel.Info` instead",
        replaceWith = ReplaceWith("LoggingLevel.Info", "io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel"),
        level = DeprecationLevel.ERROR,
    )
    public val info: io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel =
        io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel.Info

    @Deprecated(
        message = "Use `LoggingLevel.Notice` instead",
        replaceWith = ReplaceWith("LoggingLevel.Notice", "io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel"),
        level = DeprecationLevel.ERROR,
    )
    public val notice: io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel =
        io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel.Notice

    @Deprecated(
        message = "Use `LoggingLevel.Warning` instead",
        replaceWith = ReplaceWith("LoggingLevel.Warning", "io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel"),
        level = DeprecationLevel.ERROR,
    )
    public val warning: io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel =
        io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel.Warning

    @Deprecated(
        message = "Use `LoggingLevel.Error` instead",
        replaceWith = ReplaceWith("LoggingLevel.Error", "io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel"),
        level = DeprecationLevel.ERROR,
    )
    public val error: io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel =
        io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel.Error

    @Deprecated(
        message = "Use `LoggingLevel.Critical` instead",
        replaceWith = ReplaceWith("LoggingLevel.Critical", "io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel"),
        level = DeprecationLevel.ERROR,
    )
    public val critical: io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel =
        io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel.Critical

    @Deprecated(
        message = "Use `LoggingLevel.Alert` instead",
        replaceWith = ReplaceWith("LoggingLevel.Alert", "io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel"),
        level = DeprecationLevel.ERROR,
    )
    public val alert: io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel =
        io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel.Alert

    @Deprecated(
        message = "Use `LoggingLevel.Emergency` instead",
        replaceWith = ReplaceWith("LoggingLevel.Emergency", "io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel"),
        level = DeprecationLevel.ERROR,
    )
    public val emergency: io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel =
        io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel.Emergency
}

@Deprecated(
    message = "Use `LoggingMessageNotification` instead",
    replaceWith = ReplaceWith(
        "LoggingMessageNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public object LoggingMessageNotification {
    @Deprecated(
        message = "Use `LoggingMessageNotificationParams` instead",
        replaceWith = ReplaceWith(
            "LoggingMessageNotificationParams",
            "io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun Params(
        level: io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel,
        data: JsonElement,
        logger: String? = null,
        meta: JsonObject? = null,
    ): io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams =
        io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams(level, data, logger, meta)

    @Deprecated(
        message = "Use `SetLevelRequest` instead",
        replaceWith = ReplaceWith(
            "SetLevelRequest",
            "io.modelcontextprotocol.kotlin.sdk.types.SetLevelRequest",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun SetLevelRequest(
        params: io.modelcontextprotocol.kotlin.sdk.types.SetLevelRequestParams,
    ): io.modelcontextprotocol.kotlin.sdk.types.SetLevelRequest =
        io.modelcontextprotocol.kotlin.sdk.types.SetLevelRequest(params)
}

/**
 * Notification of a log message passed from server to client.
 * If no logging level request has been sent from the client,
 * the server MAY decide which messages to send automatically.
 */
@Deprecated(
    message = "Use `LoggingMessageNotification` instead",
    replaceWith = ReplaceWith(
        "LoggingMessageNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public fun LoggingMessageNotification(
    params: io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams,
): io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification =
    io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification(params)

/* Sampling */

/**
 * Hints to use for model selection.
 */
@Deprecated(
    message = "Use `ModelHint` instead",
    replaceWith = ReplaceWith("ModelHint", "io.modelcontextprotocol.kotlin.sdk.types.ModelHint"),
    level = DeprecationLevel.ERROR,
)
public typealias ModelHint = io.modelcontextprotocol.kotlin.sdk.types.ModelHint

/**
 * The server's preferences for model selection, requested by the client during sampling.
 */
@Deprecated(
    message = "Use `ModelPreferences` instead",
    replaceWith = ReplaceWith("ModelPreferences", "io.modelcontextprotocol.kotlin.sdk.types.ModelPreferences"),
    level = DeprecationLevel.ERROR,
)
public typealias ModelPreferences = io.modelcontextprotocol.kotlin.sdk.types.ModelPreferences

/**
 * Describes a message issued to or received from an LLM API.
 */
@Deprecated(
    message = "Use `SamplingMessage` instead",
    replaceWith = ReplaceWith("SamplingMessage", "io.modelcontextprotocol.kotlin.sdk.types.SamplingMessage"),
    level = DeprecationLevel.ERROR,
)
public typealias SamplingMessage = io.modelcontextprotocol.kotlin.sdk.types.SamplingMessage

@Deprecated(
    message = "Use `CreateMessageRequest` instead",
    replaceWith = ReplaceWith("CreateMessageRequest", "io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest"),
    level = DeprecationLevel.ERROR,
)
public object CreateMessageRequest {
    public val IncludeContext: io.modelcontextprotocol.kotlin.sdk.types.IncludeContext.Companion =
        io.modelcontextprotocol.kotlin.sdk.types.IncludeContext
}

/**
 * A request from the server to sample an LLM via the client.
 * The client has full discretion over which model to select.
 * The client should also inform the user before beginning sampling to allow them to inspect the request
 * (human in the loop) and decide whether to approve it.
 */
@Deprecated(
    message = "Use `CreateMessageRequest` instead",
    replaceWith = ReplaceWith("CreateMessageRequest", "io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest"),
    level = DeprecationLevel.ERROR,
)
public fun CreateMessageRequest(
    params: io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequestParams,
): io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest =
    io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest(params)

@Deprecated(
    message = "Use `StopReason` instead",
    replaceWith = ReplaceWith("StopReason", "io.modelcontextprotocol.kotlin.sdk.types.StopReason"),
    level = DeprecationLevel.ERROR,
)
public typealias StopReason = io.modelcontextprotocol.kotlin.sdk.types.StopReason

/**
 * The client's response to a sampling/create_message request from the server.
 * The client should inform the user before returning the sampled message to allow them to inspect the response
 * (human in the loop) and decide whether to allow the server to see it.
 */
@Deprecated(
    message = "Use `CreateMessageResult` instead",
    replaceWith = ReplaceWith("CreateMessageResult", "io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult"),
    level = DeprecationLevel.ERROR,
)
public typealias CreateMessageResult = io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult

/* Autocomplete */
@Deprecated(
    message = "Use `Reference` instead",
    replaceWith = ReplaceWith("Reference", "io.modelcontextprotocol.kotlin.sdk.types.Reference"),
    level = DeprecationLevel.ERROR,
)
public typealias Reference = io.modelcontextprotocol.kotlin.sdk.types.Reference

/**
 * A reference to a resource or resource template definition.
 */
@Deprecated(
    message = "Use `ResourceTemplateReference` instead",
    replaceWith = ReplaceWith(
        "ResourceTemplateReference",
        "io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplateReference",
    ),
    level = DeprecationLevel.ERROR,
)
public typealias ResourceTemplateReference = io.modelcontextprotocol.kotlin.sdk.types.ResourceTemplateReference

/**
 * Identifies a prompt.
 */
@Deprecated(
    message = "Use `PromptReference` instead",
    replaceWith = ReplaceWith("PromptReference", "io.modelcontextprotocol.kotlin.sdk.types.PromptReference"),
    level = DeprecationLevel.ERROR,
)
public typealias PromptReference = io.modelcontextprotocol.kotlin.sdk.types.PromptReference

/**
 * Identifies a prompt.
 */
@Serializable
@Deprecated(
    message = "This class will be removed.",
    level = DeprecationLevel.ERROR,
)
public data class UnknownReference(val type: String)

@Deprecated(
    message = "Use `CompleteRequest` instead",
    replaceWith = ReplaceWith("CompleteRequest", "io.modelcontextprotocol.kotlin.sdk.types.CompleteRequest"),
    level = DeprecationLevel.ERROR,
)
public object CompleteRequest {
    @Deprecated(
        message = "Use `CompleteRequestParams.Argument` instead",
        replaceWith = ReplaceWith(
            "CompleteRequestParams.Argument",
            "io.modelcontextprotocol.kotlin.sdk.types.CompleteRequestParams.Argument",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun Argument(
        name: String,
        value: String,
    ): io.modelcontextprotocol.kotlin.sdk.types.CompleteRequestParams.Argument =
        io.modelcontextprotocol.kotlin.sdk.types.CompleteRequestParams.Argument(name, value)
}

/**
 * A request from the client to the server to ask for completion options.
 */
@Deprecated(
    message = "Use `CompleteRequest` instead",
    replaceWith = ReplaceWith("CompleteRequest", "io.modelcontextprotocol.kotlin.sdk.types.CompleteRequest"),
    level = DeprecationLevel.ERROR,
)
public fun CompleteRequest(
    params: io.modelcontextprotocol.kotlin.sdk.types.CompleteRequestParams,
): io.modelcontextprotocol.kotlin.sdk.types.CompleteRequest =
    io.modelcontextprotocol.kotlin.sdk.types.CompleteRequest(params)

@Deprecated(
    message = "Use `CompleteResult` instead",
    replaceWith = ReplaceWith("CompleteResult", "io.modelcontextprotocol.kotlin.sdk.types.CompleteResult"),
    level = DeprecationLevel.ERROR,
)
public object CompleteResult {
    @Deprecated(
        message = "Use `CompleteResult.Completion` instead",
        replaceWith = ReplaceWith(
            "CompleteResult.Completion",
            "io.modelcontextprotocol.kotlin.sdk.types.CompleteResult.Completion",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun Completion(
        values: List<String>,
        total: Int? = null,
        hasMore: Boolean? = null,
    ): io.modelcontextprotocol.kotlin.sdk.types.CompleteResult.Completion =
        io.modelcontextprotocol.kotlin.sdk.types.CompleteResult.Completion(values, total, hasMore)
}

/**
 * The server's response to a completion/complete request
 */
@Deprecated(
    message = "Use `CompleteResult` instead",
    replaceWith = ReplaceWith("CompleteResult", "io.modelcontextprotocol.kotlin.sdk.types.CompleteResult"),
    level = DeprecationLevel.ERROR,
)
public fun CompleteResult(
    completion: io.modelcontextprotocol.kotlin.sdk.types.CompleteResult.Completion,
    meta: JsonObject? = null,
): io.modelcontextprotocol.kotlin.sdk.types.CompleteResult =
    io.modelcontextprotocol.kotlin.sdk.types.CompleteResult(completion, meta)

/* Roots */

/**
 * Represents a root directory or file that the server can operate on.
 */
@Deprecated(
    message = "Use `Root` instead",
    replaceWith = ReplaceWith("Root", "io.modelcontextprotocol.kotlin.sdk.types.Root"),
    level = DeprecationLevel.ERROR,
)
public typealias Root = io.modelcontextprotocol.kotlin.sdk.types.Root

/**
 * Sent from the server to request a list of root URIs from the client.
 */
@Deprecated(
    message = "Use `ListRootsRequest` instead",
    replaceWith = ReplaceWith("ListRootsRequest", "io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest"),
    level = DeprecationLevel.ERROR,
)
public typealias ListRootsRequest = io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest

/**
 * The client's response to a roots/list request from the server.
 */
@Deprecated(
    message = "Use `ListRootsResult` instead",
    replaceWith = ReplaceWith("ListRootsResult", "io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult"),
    level = DeprecationLevel.ERROR,
)
public typealias ListRootsResult = io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult

@Deprecated(
    message = "Use `RootsListChangedNotification` instead",
    replaceWith = ReplaceWith(
        "RootsListChangedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.RootsListChangedNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public object RootsListChangedNotification {
    @Deprecated(
        message = "Use `BaseNotificationParams` instead",
        replaceWith = ReplaceWith(
            "BaseNotificationParams",
            "io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams",
        ),
        level = DeprecationLevel.ERROR,
    )
    public fun Params(meta: JsonObject? = null): io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams =
        io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams(meta)
}

/**
 * A notification from the client to the server, informing it that the list of roots has changed.
 */
@Deprecated(
    message = "Use `RootsListChangedNotification` instead",
    replaceWith = ReplaceWith(
        "RootsListChangedNotification",
        "io.modelcontextprotocol.kotlin.sdk.types.RootsListChangedNotification",
    ),
    level = DeprecationLevel.ERROR,
)
public fun RootsListChangedNotification(
    params: io.modelcontextprotocol.kotlin.sdk.types.BaseNotificationParams? = null,
): io.modelcontextprotocol.kotlin.sdk.types.RootsListChangedNotification =
    io.modelcontextprotocol.kotlin.sdk.types.RootsListChangedNotification(params)

@Deprecated(
    message = "Use `ElicitRequest` instead",
    replaceWith = ReplaceWith(
        "ElicitRequest",
        "io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest",
    ),
    level = DeprecationLevel.ERROR,
)
public object CreateElicitationRequest {
    public fun RequestedSchema(
        properties: JsonObject,
        required: List<String>? = null,
    ): io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams.RequestedSchema =
        io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams.RequestedSchema(properties, required)
}

/**
 * Sent from the server to create an elicitation from the client.
 */
@Deprecated(
    message = "Use `ElicitRequest` instead",
    replaceWith = ReplaceWith(
        "ElicitRequest",
        "io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest",
    ),
    level = DeprecationLevel.ERROR,
)
public fun CreateElicitationRequest(
    params: io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams,
): io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest =
    io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest(params)

@Deprecated(
    message = "Use `ElicitResult` instead",
    replaceWith = ReplaceWith(
        "ElicitResult",
        "io.modelcontextprotocol.kotlin.sdk.types.ElicitResult",
    ),
    level = DeprecationLevel.ERROR,
)
public object CreateElicitationResult {
    @Deprecated(
        message = "Use `ElicitResult.Action` instead",
        replaceWith = ReplaceWith(
            "ElicitResult.Action",
            "io.modelcontextprotocol.kotlin.sdk.types.ElicitResult.Action",
        ),
        level = DeprecationLevel.ERROR,
    )
    public object Action {
        @Deprecated(
            message = "Use `ElicitResult.Action.Accept` instead",
            replaceWith = ReplaceWith(
                "ElicitResult.Action.Accept",
                "io.modelcontextprotocol.kotlin.sdk.types.ElicitResult.Action",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val accept: io.modelcontextprotocol.kotlin.sdk.types.ElicitResult.Action =
            io.modelcontextprotocol.kotlin.sdk.types.ElicitResult.Action.Accept

        @Deprecated(
            message = "Use `ElicitResult.Action.Decline` instead",
            replaceWith = ReplaceWith(
                "ElicitResult.Action.Decline",
                "io.modelcontextprotocol.kotlin.sdk.types.ElicitResult.Action",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val decline: io.modelcontextprotocol.kotlin.sdk.types.ElicitResult.Action =
            io.modelcontextprotocol.kotlin.sdk.types.ElicitResult.Action.Decline

        @Deprecated(
            message = "Use `ElicitResult.Action.Cancel` instead",
            replaceWith = ReplaceWith(
                "ElicitResult.Action.Cancel",
                "io.modelcontextprotocol.kotlin.sdk.types.ElicitResult.Action",
            ),
            level = DeprecationLevel.ERROR,
        )
        public val cancel: io.modelcontextprotocol.kotlin.sdk.types.ElicitResult.Action =
            io.modelcontextprotocol.kotlin.sdk.types.ElicitResult.Action.Cancel
    }
}

/**
 * The client's response to an elicitation/create request from the server.
 */
@Deprecated(
    message = "Use `ElicitResult` instead",
    replaceWith = ReplaceWith(
        "ElicitResult",
        "io.modelcontextprotocol.kotlin.sdk.types.ElicitResult",
    ),
    level = DeprecationLevel.ERROR,
)
public fun CreateElicitationResult(
    action: io.modelcontextprotocol.kotlin.sdk.types.ElicitResult.Action,
    content: JsonObject? = null,
    meta: JsonObject? = null,
): io.modelcontextprotocol.kotlin.sdk.types.ElicitResult =
    io.modelcontextprotocol.kotlin.sdk.types.ElicitResult(action, content, meta)

/**
 * Represents an error specific to the MCP protocol.
 */
@Deprecated(
    message = "Use `McpException` instead",
    replaceWith = ReplaceWith("McpException"),
    level = DeprecationLevel.ERROR,
)
public typealias McpError = io.modelcontextprotocol.kotlin.sdk.types.McpException
