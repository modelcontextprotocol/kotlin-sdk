package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
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

/**
 * Methods for communicating from the server back to the client.
 */
@ExperimentalMcpApi
public class ClientConnection internal constructor(private val session: ServerSession) {

    /**
     * Registers a callback to be invoked when the server session is closing.
     */
    public fun onClose(block: () -> Unit) {
        session.onClose(block)
    }

    /**
     * The capabilities supported by the server, related to the session.
     */
    public val serverCapabilities: ServerCapabilities get() = session.serverCapabilities

    /**
     * The client's reported capabilities after initialization.
     */
    public val clientCapabilities: ClientCapabilities get() = session.clientCapabilities ?: error("Session not yet initialized")

    /**
     * The client's version information.
     */
    public val clientVersion: Implementation get() = session.clientVersion ?: error("Session not yet initialized")

    /**
     * A unique identifier identifying the current session.
     * Has no inherent meaning.
     */
    public val sessionId: String get() = session.sessionId

    /**
     * Sends a request and waits for a response.
     *
     * Do not use this method to emit notifications! Use notification() instead.
     */
    public suspend fun <T : RequestResult> request(request: Request, options: RequestOptions? = null): T {
        logger.trace { "Sending request to client for session $sessionId: $request" }
        return session.request(request, options)
    }

    /**
     * Emits a notification, which is a one-way message that does not expect a response.
     */
    public suspend fun notification(notification: Notification, relatedRequestId: RequestId? = null) {
        logger.trace { "Sending notification to client for session $sessionId: $notification" }
        session.notification(notification, relatedRequestId)
    }

    /**
     * Sends a ping request to the client to check connectivity.
     *
     * @return The result of the ping request.
     * @throws IllegalStateException If for some reason the method is not supported or the connection is closed.
     */
    public suspend fun ping(): EmptyResult = request(PingRequest())

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
    ): CreateMessageResult {
        logger.debug {
            "Creating message with ${params.params.messages.size} messages, maxTokens=${params.params.maxTokens}, temperature=${params.params.temperature}, systemPrompt=${if (params.params.systemPrompt != null) "present" else "absent"}"
        }
        logger.trace { "Full createMessage params: $params" }
        return request(params, options)
    }

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
    ): ListRootsResult {
        logger.debug { "Listing roots with params: $params" }
        return request(ListRootsRequest(BaseRequestParams(RequestMeta(params))), options)
    }

    /**
     * Sends a message to the client requesting an elicitation.
     * This typically results in a form being displayed to the end user.
     *
     * @param message The message for the elicitation to display.
     * @param requestedSchema The schema requested by the client for the elicitation result. Influences the form displayed to the user.
     * @param options Optional request options.
     * @return The result of the elicitation request.
     * @throws IllegalStateException If the server or client does not support elicitation.
     */
    public suspend fun createElicitation(
        message: String,
        requestedSchema: ElicitRequestParams.RequestedSchema,
        options: RequestOptions? = null,
    ): ElicitResult {
        logger.debug {
            "Creating elicitation with message length=${message.length}, " +
                "schema properties count=${requestedSchema.properties.size}"
        }
        logger.trace { "Full elicitation message: $message, requestedSchema: $requestedSchema" }
        return request(ElicitRequest(ElicitRequestParams(message, requestedSchema)), options)
    }

    /**
     * Sends a logging message notification to the client.
     * Messages are filtered based on the current logging level set by the client.
     * If no logging level is set, all messages are sent.
     *
     * @param notification The logging message notification.
     */
    public suspend fun sendLoggingMessage(notification: LoggingMessageNotification) {
        if (serverCapabilities.logging != null) {
            if (isMessageAccepted(notification.params.level)) {
                logger.trace { "Sending logging message: ${notification.params.data}" }
                notification(notification)
            } else {
                logger.trace { "Filtering out logging message with level ${notification.params.level}" }
            }
        }
    }

    /**
     * Sends a resource-updated notification to the client, indicating that a specific resource has changed.
     *
     * @param notification Details of the updated resource.
     */
    public suspend fun sendResourceUpdated(notification: ResourceUpdatedNotification) {
        logger.debug { "Sending resource updated notification for: ${notification.params.uri}" }
        notification(notification)
    }

    /**
     * Sends a notification to the client indicating that the list of resources has changed.
     */
    public suspend fun sendResourceListChanged() {
        logger.debug { "Sending resource list changed notification" }
        notification(ResourceListChangedNotification())
    }

    /**
     * Sends a notification to the client indicating that the list of tools has changed.
     */
    public suspend fun sendToolListChanged() {
        logger.debug { "Sending tool list changed notification" }
        notification(ToolListChangedNotification())
    }

    /**
     * Sends a notification to the client indicating that the list of prompts has changed.
     */
    public suspend fun sendPromptListChanged() {
        logger.debug { "Sending prompt list changed notification" }
        notification(PromptListChangedNotification())
    }

    /**
     * Checks if a message with the given level should be ignored based on the current logging level.
     *
     * @param level The level of the message to check.
     * @return true if the message should be ignored (filtered out), false otherwise.
     */
    private fun isMessageIgnored(level: LoggingLevel): Boolean {
        val current = session.currentLoggingLevel.value ?: return false // If no level is set, don't filter

        return level < current
    }

    /**
     * Checks if a message with the given level should be accepted based on the current logging level.
     *
     * @param level The level of the message to check.
     * @return true if the message should be accepted (not filtered out), false otherwise.
     */
    private fun isMessageAccepted(level: LoggingLevel): Boolean = !isMessageIgnored(level)
}
