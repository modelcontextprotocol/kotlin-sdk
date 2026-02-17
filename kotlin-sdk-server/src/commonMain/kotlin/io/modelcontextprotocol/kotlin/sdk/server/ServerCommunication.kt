package io.modelcontextprotocol.kotlin.sdk.server

import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import kotlinx.serialization.json.JsonObject

/**
 * Methods for communicating from the server back to the client.
 */
public interface ServerCommunication {
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
     *          Influences the form displayed to the user.
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
}
