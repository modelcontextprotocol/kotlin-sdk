@file:OptIn(ExperimentalSerializationApi::class)

package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Represents a notification in the protocol.
 */
@Serializable(with = NotificationPolymorphicSerializer::class)
public sealed interface Notification {
    public val method: Method
    public val params: NotificationParams?
}

/**
 * Represents a notification sent by the client.
 */
@Serializable(with = ClientNotificationPolymorphicSerializer::class)
public sealed interface ClientNotification : Notification

/**
 * Represents a notification sent by the server.
 */
@Serializable(with = ServerNotificationPolymorphicSerializer::class)
public sealed interface ServerNotification : Notification

/**
 * Interface for notification parameter types.
 *
 * @property meta Optional metadata for the notification.
 */
@Serializable
public sealed interface NotificationParams : WithMeta

/**
 * Base parameters for notifications that only contain metadata.
 */
@Serializable
public data class BaseNotificationParams(@SerialName("_meta") override val meta: JsonObject? = null) :
    NotificationParams

/**
 * Represents a progress notification.
 *
 * @property progress The progress thus far. This should increase every time progress is made,
 * even if the total is unknown.
 * @property total Total number of items to a process (or total progress required), if known.
 * @property message An optional message describing the current progress.
 */
@Serializable
public class Progress(
    public val progress: Double,
    public val total: Double? = null,
    public val message: String? = null,
)

// ============================================================================
// Custom Notification
// ============================================================================

/**
 * Represents a custom notification method that is not part of the core MCP specification.
 *
 * The MCP protocol allows implementations to define custom methods for extending functionality.
 * This class captures such custom notifications while preserving all their data.
 *
 * @property method The custom method name. By convention, custom methods often contain
 * organization-specific prefixes (e.g., "mycompany/custom_event").
 * @property params Raw JSON parameters for the custom notification, if present.
 */
@Serializable
public data class CustomNotification(override val method: Method, override val params: BaseNotificationParams? = null) :
    ClientNotification,
    ServerNotification

// ============================================================================
// Cancelled Notification
// ============================================================================

/**
 * This notification can be sent by either side to indicate that it is cancelling a previously-issued request.
 *
 * The request SHOULD still be in-flight, but due to communication latency,
 * it is always possible that this notification MAY arrive after the request has already finished.
 *
 * This notification indicates that the result will be unused, so any associated processing SHOULD cease.
 *
 * A client MUST NOT attempt to cancel its `initialize` request.
 *
 * @property params Details of the cancellation request.
 */
@Serializable
public data class CancelledNotification(override val params: CancelledNotificationParams) :
    ClientNotification,
    ServerNotification {
    @EncodeDefault
    override val method: Method = Method.Defined.NotificationsCancelled
}

/**
 * Parameters for a notifications/cancelled notification.
 *
 * @property requestId The ID of the request to cancel.
 * This MUST correspond to the ID of a request previously issued in the same direction.
 * @property reason An optional string describing the reason for the cancellation.
 * This MAY be logged or presented to the user.
 * @property meta Optional metadata for this notification.
 */
@Serializable
public data class CancelledNotificationParams(
    val requestId: RequestId,
    val reason: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : NotificationParams

// ============================================================================
// Initialized Notification
// ============================================================================

/**
 * This notification is sent from the client to the server after initialization has finished.
 *
 * The client sends this after receiving the [InitializeResult] to signal that it is ready
 * to begin normal operations.
 *
 * @property params Optional notification parameters containing metadata.
 */
@Serializable
public data class InitializedNotification(override val params: BaseNotificationParams? = null) : ClientNotification {
    @EncodeDefault
    override val method: Method = Method.Defined.NotificationsInitialized
}

// ============================================================================
// Logging Message Notification
// ============================================================================

/**
 * A notification of a log message passed from server to client.
 *
 * If no [SetLevelRequest] has been sent from the client, the server MAY decide
 * which messages to send automatically.
 *
 * @property params The log message parameters including level and data.
 */
@Serializable
public data class LoggingMessageNotification(override val params: LoggingMessageNotificationParams) :
    ServerNotification {
    @EncodeDefault
    override val method: Method = Method.Defined.NotificationsMessage
}

/**
 * Parameters for a notifications/message notification.
 *
 * @property level The severity of this log message.
 * @property data The data to be logged, such as a string message or an object.
 * Any JSON serializable type is allowed here.
 * @property logger An optional name of the logger issuing this message.
 * @property meta Optional metadata for this notification.
 */
@Serializable
public data class LoggingMessageNotificationParams(
    val level: LoggingLevel,
    val data: JsonElement,
    val logger: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : NotificationParams

// ============================================================================
// Progress Notification
// ============================================================================

/**
 * An out-of-band notification used to inform the receiver of a progress update for a long-running request.
 *
 * This notification can be sent by either the client or server to provide progress updates
 * for requests that include a progressToken in their _meta field.
 *
 * @property params The progress update parameters.
 */
@Serializable
public data class ProgressNotification(override val params: ProgressNotificationParams) :
    ClientNotification,
    ServerNotification {
    @EncodeDefault
    override val method: Method = Method.Defined.NotificationsProgress
}

/**
 * Parameters for a notifications/progress notification.
 *
 * @property progressToken The progress token which was given in the initial request,
 * used to associate this notification with the request that is proceeding.
 * @property progress The progress thus far. This should increase every time progress is made,
 * even if the total is unknown.
 * @property total Total number of items to process (or total progress required), if known.
 * @property message An optional message describing the current progress.
 * @property meta Optional metadata for this notification.
 */
@Serializable
public data class ProgressNotificationParams(
    val progressToken: ProgressToken,
    val progress: Double,
    val total: Double? = null,
    val message: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : NotificationParams

// ============================================================================
// Prompts List Changed Notification
// ============================================================================

/**
 * An optional notification from the server to the client, informing it that the list of prompts it offers has changed.
 *
 * Servers may issue this without any previous subscription from the client.
 * Sent only if the server's [ServerCapabilities.prompts] has `listChanged = true`.
 *
 * @property params Optional notification parameters containing metadata.
 */
@Serializable
public data class PromptListChangedNotification(override val params: BaseNotificationParams? = null) :
    ServerNotification {
    @EncodeDefault
    override val method: Method = Method.Defined.NotificationsPromptsListChanged
}

// ============================================================================
// Resources List Changed Notification
// ============================================================================

/**
 * An optional notification from the server to the client, informing it that the list of resources it can read from has changed.
 *
 * Servers may issue this without any previous subscription from the client.
 * Sent only if the server's [ServerCapabilities.resources] has `listChanged = true`.
 *
 * @property params Optional notification parameters containing metadata.
 */
@Serializable
public data class ResourceListChangedNotification(override val params: BaseNotificationParams? = null) :
    ServerNotification {
    @EncodeDefault
    override val method: Method = Method.Defined.NotificationsResourcesListChanged
}

// ============================================================================
// Resource Updated Notification
// ============================================================================

/**
 * A notification from the server to the client, informing it that a resource has changed and may need to be read again.
 *
 * This should only be sent if the client previously sent a resources/subscribe request
 * and the server's [ServerCapabilities.resources] has `subscribe = true`.
 *
 * @property params Parameters identifying which resource was updated.
 */
@Serializable
public data class ResourceUpdatedNotification(override val params: ResourceUpdatedNotificationParams) :
    ServerNotification {
    @EncodeDefault
    override val method: Method = Method.Defined.NotificationsResourcesUpdated
}

/**
 * Parameters for a notifications/resources/updated notification.
 *
 * @property uri The URI of the resource that has been updated.
 * This might be a sub-resource of the one that the client actually subscribed to.
 * @property meta Optional metadata for this notification.
 */
@Serializable
public data class ResourceUpdatedNotificationParams(
    val uri: String,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : NotificationParams

// ============================================================================
// Roots List Changed Notification
// ============================================================================

/**
 * A notification from the client to the server, informing it that the list of roots has changed.
 *
 * This notification should be sent whenever the client adds, removes, or modifies any root.
 * The server should then request an updated list of roots using the ListRootsRequest.
 * Sent only if the client's [ClientCapabilities.roots] has `listChanged = true`.
 *
 * @property params Optional notification parameters containing metadata.
 */
@Serializable
public data class RootsListChangedNotification(override val params: BaseNotificationParams? = null) :
    ClientNotification {
    @EncodeDefault
    override val method: Method = Method.Defined.NotificationsRootsListChanged
}

// ============================================================================
// Tools List Changed Notification
// ============================================================================

/**
 * An optional notification from the server to the client, informing it that the list of tools it offers has changed.
 *
 * Servers may issue this without any previous subscription from the client.
 * Sent only if the server's [ServerCapabilities.tools] has `listChanged = true`.
 *
 * @property params Optional notification parameters containing metadata.
 */
@Serializable
public data class ToolListChangedNotification(override val params: BaseNotificationParams? = null) :
    ServerNotification {
    @EncodeDefault
    override val method: Method = Method.Defined.NotificationsToolsListChanged
}
