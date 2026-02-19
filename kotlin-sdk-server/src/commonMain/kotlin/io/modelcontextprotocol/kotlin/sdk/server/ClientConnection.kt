package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.Request
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerNotification
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification

private val logger = KotlinLogging.logger {}

/**
 * Represents a connection interface between a server and a client, enabling
 * communication through notifications, requests, and other operations.
 * This interface defines various methods to facilitate the interaction.
 */
@Suppress("TooManyFunctions")
public interface ClientConnection {

    /**
     * A unique identifier identifying the current session.
     * Has no inherent meaning.
     */
    public val sessionId: String

    /**
     * Sends a server-side notification to the client.
     *
     * @param notification The notification to be sent to the client. This typically represents
     *                     a specific event or update from the server.
     * @param relatedRequestId An optional identifier linking this notification to a prior request.
     *                         Useful for correlating notifications with originating requests.
     */
    public suspend fun notification(notification: ServerNotification, relatedRequestId: RequestId? = null)

    /**
     * Sends a ping request to the client to check connectivity.
     *
     * @param request Optional request parameters for the ping operation.
     * @param options Optional request options, such as timeout or progress callback settings.
     * @return The result of the ping request.
     * @throws IllegalStateException If for some reason the method is not supported or the connection is closed.
     */
    public suspend fun ping(request: PingRequest = PingRequest(), options: RequestOptions? = null): EmptyResult

    /**
     * Creates a message using the server's sampling capability.
     *
     * @param request The parameters for creating a message.
     * @param options Optional request options.
     * @return The created message result.
     * @throws IllegalStateException If the server does not support sampling or if the request fails.
     */
    public suspend fun createMessage(
        request: CreateMessageRequest,
        options: RequestOptions? = null,
    ): CreateMessageResult

    /**
     * Lists the available "roots" from the client's perspective (if supported).
     *
     * Roots define the boundaries of where servers can operate within the filesystem,
     * allowing them to understand which directories and files they have access to
     *
     * @param request Optional request parameters containing metadata for the list roots operation.
     * @param options Optional request options, such as timeout or progress callback settings.
     * @return The list of roots.
     * @throws IllegalStateException If the server or client does not support roots.
     */
    public suspend fun listRoots(
        request: ListRootsRequest = ListRootsRequest(),
        options: RequestOptions? = null,
    ): ListRootsResult

    /**
     * Sends a message to the client requesting elicitation.
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
     * Sends a message to the client requesting elicitation.
     * This typically results in a form being displayed to the user.
     *
     * @param request The elicitation request parameters.
     * @param options Optional request options.
     * @return The result of the elicitation request.
     * @throws IllegalStateException If the server or client does not support elicitation.
     */
    public suspend fun createElicitation(request: ElicitRequest, options: RequestOptions? = null): ElicitResult

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
}

@Suppress("TooManyFunctions")
internal class ClientConnectionImpl(private val session: ServerSession) : ClientConnection {

    override val sessionId: String get() = session.sessionId

    private suspend fun <T : RequestResult> request(request: Request, options: RequestOptions? = null): T {
        logger.trace { "Sending request to client for session $sessionId: $request" }
        return session.request(request, options)
    }

    override suspend fun notification(notification: ServerNotification, relatedRequestId: RequestId?) {
        if (notification is LoggingMessageNotification && !isMessageAccepted(notification.params.level)) {
            logger.trace { "Filtering out logging message with level ${notification.params.level}" }
            return
        }
        logger.trace { "Sending notification to client for session $sessionId: $notification" }
        session.notification(notification, relatedRequestId)
    }

    override suspend fun ping(request: PingRequest, options: RequestOptions?): EmptyResult {
        logger.trace { "Sending ping request: $request" }
        return request(request, options)
    }

    override suspend fun createMessage(request: CreateMessageRequest, options: RequestOptions?): CreateMessageResult {
        with(request.params) {
            logger.debug {
                "Creating message with ${messages.size} messages, maxTokens=$maxTokens, " +
                    "temperature=$temperature, " +
                    "systemPrompt=${if (systemPrompt != null) "present" else "absent"}"
            }
        }
        logger.trace { "Full createMessage params: $request" }
        return request(request, options)
    }

    override suspend fun listRoots(request: ListRootsRequest, options: RequestOptions?): ListRootsResult {
        logger.debug { "Listing roots" }
        return request(request, options)
    }

    override suspend fun createElicitation(request: ElicitRequest, options: RequestOptions?): ElicitResult {
        with(request.params) {
            logger.debug {
                "Creating elicitation with message length=${message.length}, " +
                    "schema properties count=${requestedSchema.properties.size}"
            }
        }
        logger.trace { "ElicitRequest: $request" }
        return request(request, options)
    }

    override suspend fun createElicitation(
        message: String,
        requestedSchema: ElicitRequestParams.RequestedSchema,
        options: RequestOptions?,
    ): ElicitResult = createElicitation(
        request = ElicitRequest(params = ElicitRequestParams(message, requestedSchema)),
        options = options,
    )

    override suspend fun sendLoggingMessage(notification: LoggingMessageNotification) {
        notification(notification)
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

    /**
     * Determines whether a message with the specified logging level is accepted
     * based on the current logging level of the session.
     *
     * @param level The logging level of the message to be evaluated.
     * @return True if the message is accepted (i.e., its level meets or exceeds
     *         the current session's logging level), or if no current logging
     *         level is set. Otherwise, false.
     */
    private fun isMessageAccepted(level: LoggingLevel): Boolean {
        val current = session.currentLoggingLevel.value ?: return true // If no level is set, don't filter

        return level >= current
    }
}
