package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.BaseRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.Notification
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.Request
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger {}

@Suppress("TooManyFunctions")
public interface ClientConnection {
    /**
     * The capabilities supported by the server, related to the session.
     */
    public val serverCapabilities: ServerCapabilities

    /**
     * The client's reported capabilities after initialization.
     */
    public val clientCapabilities: ClientCapabilities

    /**
     * The client's version information.
     */
    public val clientVersion: Implementation

    /**
     * A unique identifier identifying the current session.
     * Has no inherent meaning.
     */
    public val sessionId: String

    /**
     * Registers a callback to be invoked when the server session is closing.
     */
    public fun onClose(block: () -> Unit)

    /**
     * Sends a request and waits for a response.
     *
     * Do not use this method to emit notifications! Use notification() instead.
     */
    public suspend fun <T : RequestResult> request(request: Request, options: RequestOptions? = null): T

    /**
     * Emits a notification, which is a one-way message that does not expect a response.
     */
    public suspend fun notification(notification: Notification, relatedRequestId: RequestId? = null)

    /**
     * Sends a ping request to the client to check connectivity.
     *
     * @return The result of the ping request.
     * @throws IllegalStateException If for some reason the method is not supported or the connection is closed.
     */
    public suspend fun ping(): EmptyResult

    /**
     * Creates a message using the server's sampling capability.
     *
     * @param params The parameters for creating a message.
     * @param options Optional request options.
     * @return The created message result.
     * @throws IllegalStateException If the server does not support sampling or if the request fails.
     */
    public suspend fun createMessage(
        params: CreateMessageRequest,
        options: RequestOptions? = null,
    ): CreateMessageResult

    /**
     * Lists the available "roots" from the client's perspective (if supported).
     *
     * @param params JSON parameters for the request, usually empty.
     * @param options Optional request options.
     * @return The list of roots.
     * @throws IllegalStateException If the server or client does not support roots.
     */
    public suspend fun listRoots(
        params: JsonObject = EmptyJsonObject,
        options: RequestOptions? = null,
    ): ListRootsResult

    /**
     * Sends a message to the client requesting an elicitation.
     * This typically results in a form being displayed to the end user.
     *
     * @param message The message for the elicitation to display.
     * @param requestedSchema The schema requested by the client for the elicitation result.
     * Influences the form displayed to the user.
     * @param options Optional request options.
     * @return The result of the elicitation request.
     * @throws IllegalStateException If the server or client does not support elicitation.
     */
    public suspend fun createElicitation(
        message: String,
        requestedSchema: ElicitRequestParams.RequestedSchema,
        options: RequestOptions? = null,
    ): ElicitResult

    /**
     * Sends a logging message notification to the client.
     * Messages are filtered based on the current logging level set by the client.
     * If no logging level is set, all messages are sent.
     *
     * @param notification The logging message notification.
     */
    public suspend fun sendLoggingMessage(notification: LoggingMessageNotification)

    /**
     * Sends a resource-updated notification to the client, indicating that a specific resource has changed.
     *
     * @param notification Details of the updated resource.
     */
    public suspend fun sendResourceUpdated(notification: ResourceUpdatedNotification)

    /**
     * Sends a notification to the client indicating that the list of resources has changed.
     */
    public suspend fun sendResourceListChanged()

    /**
     * Sends a notification to the client indicating that the list of tools has changed.
     */
    public suspend fun sendToolListChanged()

    /**
     * Sends a notification to the client indicating that the list of prompts has changed.
     */
    public suspend fun sendPromptListChanged()

    /**
     * Checks if a message with the given level should be ignored based on the current logging level.
     *
     * @param level The level of the message to check.
     * @return true if the message should be ignored (filtered out), false otherwise.
     */
    public fun isMessageIgnored(level: LoggingLevel): Boolean

    /**
     * Checks if a message with the given level should be accepted based on the current logging level.
     *
     * @param level The level of the message to check.
     * @return true if the message should be accepted (not filtered out), false otherwise.
     */
    public fun isMessageAccepted(level: LoggingLevel): Boolean
}

/**
 * Methods for communicating from the server back to the client.
 */
@Suppress("TooManyFunctions")
internal class ClientConnectionImpl(private val session: ServerSession) : ClientConnection {

    override fun onClose(block: () -> Unit) {
        session.onClose(block)
    }

    override val serverCapabilities: ServerCapabilities get() = session.serverCapabilities

    override val clientCapabilities: ClientCapabilities
        get() = session.clientCapabilities
            ?: error("Session not yet initialized")

    override val clientVersion: Implementation get() = session.clientVersion ?: error("Session not yet initialized")

    override val sessionId: String get() = session.sessionId

    override suspend fun <T : RequestResult> request(request: Request, options: RequestOptions?): T {
        logger.trace { "Sending request to client for session $sessionId: $request" }
        return session.request(request, options)
    }

    override suspend fun notification(notification: Notification, relatedRequestId: RequestId?) {
        logger.trace { "Sending notification to client for session $sessionId: $notification" }
        session.notification(notification, relatedRequestId)
    }

    override suspend fun ping(): EmptyResult = request(PingRequest())

    override suspend fun createMessage(params: CreateMessageRequest, options: RequestOptions?): CreateMessageResult {
        logger.debug {
            "Creating message with ${params.params.messages.size} messages, maxTokens=${params.params.maxTokens}, " +
                "temperature=${params.params.temperature}, systemPrompt=${if (params.params.systemPrompt != null) "present" else "absent"}"
        }
        logger.trace { "Full createMessage params: $params" }
        return request(params, options)
    }

    override suspend fun listRoots(params: JsonObject, options: RequestOptions?): ListRootsResult {
        logger.debug { "Listing roots with params: $params" }
        return request(ListRootsRequest(BaseRequestParams(RequestMeta(params))), options)
    }

    override suspend fun createElicitation(
        message: String,
        requestedSchema: ElicitRequestParams.RequestedSchema,
        options: RequestOptions?,
    ): ElicitResult {
        logger.debug {
            "Creating elicitation with message length=${message.length}, " +
                "schema properties count=${requestedSchema.properties.size}"
        }
        logger.trace { "Full elicitation message: $message, requestedSchema: $requestedSchema" }
        return request(ElicitRequest(ElicitRequestParams(message, requestedSchema)), options)
    }

    override suspend fun sendLoggingMessage(notification: LoggingMessageNotification) {
        if (serverCapabilities.logging != null) {
            if (isMessageAccepted(notification.params.level)) {
                logger.trace { "Sending logging message: ${notification.params.data}" }
                notification(notification)
            } else {
                logger.trace { "Filtering out logging message with level ${notification.params.level}" }
            }
        }
    }

    override suspend fun sendResourceUpdated(notification: ResourceUpdatedNotification) {
        logger.debug { "Sending resource updated notification for: ${notification.params.uri}" }
        notification(notification)
    }

    override suspend fun sendResourceListChanged() {
        logger.debug { "Sending resource list changed notification" }
        notification(ResourceListChangedNotification())
    }

    override suspend fun sendToolListChanged() {
        logger.debug { "Sending tool list changed notification" }
        notification(ToolListChangedNotification())
    }

    override suspend fun sendPromptListChanged() {
        logger.debug { "Sending prompt list changed notification" }
        notification(PromptListChangedNotification())
    }

    override fun isMessageIgnored(level: LoggingLevel): Boolean {
        val current = session.currentLoggingLevel.value ?: return false // If no level is set, don't filter

        return level < current
    }

    override fun isMessageAccepted(level: LoggingLevel): Boolean = !isMessageIgnored(level)
}
