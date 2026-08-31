package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.shared.currentRequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestFormParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestURLParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitationCompleteNotification
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.IncludeContext
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.MissingRequiredClientCapabilityData
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.PromptListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.Request
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import io.modelcontextprotocol.kotlin.sdk.types.ResourceListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerNotification
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.isModernProtocolVersion
import io.modelcontextprotocol.kotlin.sdk.types.missingIn
import io.modelcontextprotocol.kotlin.sdk.types.supportsUrl
import kotlinx.serialization.json.encodeToJsonElement

private val logger = KotlinLogging.logger {}

/**
 * Represents a connection interface between a server and a client, enabling
 * communication through notifications, requests, and other operations.
 * This interface defines various methods to facilitate the interaction.
 */
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
     * @throws McpException with [RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY] if the
     * client did not declare the sampling capability this request needs.
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
     * @throws McpException with [RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY] if the
     * client did not declare the roots capability.
     */
    public suspend fun listRoots(
        request: ListRootsRequest = ListRootsRequest(),
        options: RequestOptions? = null,
    ): ListRootsResult

    /**
     * Sends a message to the client requesting elicitation.
     * This typically results in a form being displayed to the end user.
     *
     * Accepted content is validated against [requestedSchema]. If content is absent, an empty
     * object is used for validation only, so declared `required` properties still apply. The
     * returned [ElicitResult] is not modified.
     *
     * @param message The message for the elicitation to display.
     * @param requestedSchema The schema the server requests for the elicitation result.
     * Influences the form displayed to the user.
     * @param options Optional request options.
     * @return The result of the elicitation request.
     * @throws McpException with [RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY] if the
     * client did not declare the elicitation mode this request needs, or if an accepted response
     * does not match [requestedSchema].
     */
    public suspend fun createElicitation(
        message: String,
        requestedSchema: ElicitRequestParams.RequestedSchema,
        options: RequestOptions? = null,
    ): ElicitResult

    /**
     * Sends a URL mode elicitation request to the client, directing the user
     * to an external URL for out-of-band interactions.
     *
     * @param message The message explaining why the interaction is needed.
     * @param elicitationId A unique identifier for the elicitation.
     * @param url The URL that the user should navigate to.
     * @param options Optional request options.
     * @return The result of the elicitation request.
     * @throws McpException with [RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY] if the
     * client did not declare the elicitation mode this request needs.
     */
    public suspend fun createElicitation(
        message: String,
        elicitationId: String,
        url: String,
        options: RequestOptions? = null,
    ): ElicitResult

    /**
     * Sends a message to the client requesting elicitation.
     * This typically results in a form being displayed to the user.
     *
     * For form-mode requests, accepted content is validated against the requested schema. If
     * content is absent, an empty object is used for validation only, so declared `required`
     * properties still apply. The returned [ElicitResult] is not modified.
     *
     * @param request The elicitation request parameters.
     * @param options Optional request options.
     * @return The result of the elicitation request.
     * @throws McpException with [RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY] if the
     * client did not declare the elicitation mode this request needs, or if an accepted form-mode
     * response does not match its requested schema.
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

    /**
     * Sends a notification to the client indicating that an out-of-band elicitation has completed.
     *
     * @param notification Details of the completed elicitation.
     */
    public suspend fun sendElicitationComplete(notification: ElicitationCompleteNotification)
}

@OptIn(ExperimentalMcpApi::class)
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
        val params = request.params

        val needsTools = params.tools != null || params.toolChoice != null
        requireClientCapabilities(
            ClientCapabilities(
                sampling = ClientCapabilities.Sampling(tools = EmptyJsonObject.takeIf { needsTools }),
            ),
        )
        requireBackChannel(Method.Defined.SamplingCreateMessage)

        val caps = governingClientCapabilities()
        if (params.includeContext != null && params.includeContext != IncludeContext.None) {
            if (caps?.sampling?.context == null) {
                logger.warn {
                    "Client did not advertise sampling.context capability but server requested " +
                        "includeContext=${params.includeContext}. This is soft-deprecated and may be " +
                        "rejected by future spec versions."
                }
            }
        }

        validateSamplingMessages(params.messages)

        logger.debug {
            "Creating message with ${params.messages.size} messages, maxTokens=${params.maxTokens}, " +
                "temperature=${params.temperature}, " +
                "systemPrompt=${if (params.systemPrompt != null) "present" else "absent"}, " +
                "tools=${params.tools?.size ?: 0}, toolChoice=${params.toolChoice?.mode}"
        }
        logger.trace { "Full createMessage params: $request" }
        return request(request, options)
    }

    override suspend fun listRoots(request: ListRootsRequest, options: RequestOptions?): ListRootsResult {
        requireClientCapabilities(ClientCapabilities(roots = ClientCapabilities.Roots()))
        requireBackChannel(Method.Defined.RootsList)
        logger.debug { "Listing roots" }
        return request(request, options)
    }

    override suspend fun createElicitation(request: ElicitRequest, options: RequestOptions?): ElicitResult {
        val params = request.params
        requireClientCapabilities(
            ClientCapabilities(
                elicitation = when (params) {
                    is ElicitRequestURLParams -> ClientCapabilities.Elicitation(url = EmptyJsonObject)
                    is ElicitRequestFormParams -> ClientCapabilities.Elicitation(form = EmptyJsonObject)
                },
            ),
        )
        requireBackChannel(Method.Defined.ElicitationCreate)
        logger.debug {
            when (params) {
                is ElicitRequestFormParams ->
                    "Creating form elicitation with message length=${params.message.length}, " +
                        "schema properties count=${params.requestedSchema.properties.size}"

                is ElicitRequestURLParams ->
                    "Creating URL elicitation with message length=${params.message.length}, " +
                        "elicitationId=${params.elicitationId}"
            }
        }
        logger.trace { "ElicitRequest: $request" }
        val result: ElicitResult = request(request, options)
        if (params is ElicitRequestFormParams && result.action == ElicitResult.Action.Accept) {
            // Peer-response backstop: accepted form content is client-supplied. Absent content is
            // validated as an empty object so declared `required` properties still apply.
            validateElicitationContent(params.requestedSchema, result.content ?: EmptyJsonObject)
        }
        return result
    }

    override suspend fun createElicitation(
        message: String,
        requestedSchema: ElicitRequestParams.RequestedSchema,
        options: RequestOptions?,
    ): ElicitResult = createElicitation(
        request = ElicitRequest(params = ElicitRequestFormParams(message = message, requestedSchema = requestedSchema)),
        options = options,
    )

    override suspend fun createElicitation(
        message: String,
        elicitationId: String,
        url: String,
        options: RequestOptions?,
    ): ElicitResult = createElicitation(
        request = ElicitRequest(
            params = ElicitRequestURLParams(message = message, elicitationId = elicitationId, url = url),
        ),
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

    override suspend fun sendElicitationComplete(notification: ElicitationCompleteNotification) {
        require(session.clientCapabilities?.elicitation.supportsUrl) {
            "Client did not advertise elicitation.url capability; cannot send an elicitation completion notification."
        }
        logger.debug { "Sending elicitation complete notification for: ${notification.params.elicitationId}" }
        notification(notification)
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
    private suspend fun isMessageAccepted(level: LoggingLevel): Boolean {
        val extra = currentRequestHandlerExtra()
            // Outside a handler there is no request to have opted in, so nothing is deliverable on
            // the request-scoped lifecycle and the connection-scoped filter applies on the other.
            ?: return session.protocolVersion?.let { isModernProtocolVersion(it) } != true &&
                acceptedByLevelSetting(level)
        if (level !in allowedLogLevels(extra.protocolVersion, extra.meta)) return false
        return acceptedByLevelSetting(level)
    }

    /** Whether a `logging/setLevel` the client issued on this connection admits [level]. */
    private fun acceptedByLevelSetting(level: LoggingLevel): Boolean {
        val current = session.currentLoggingLevel.value ?: return true // If no level is set, don't filter

        return level >= current
    }

    /**
     * The capabilities governing the request being served, or the connection's declaration outside
     * a handler.
     *
     * Under the request-scoped lifecycle every request carries its own declaration; reading the
     * connection's would let one request borrow another's.
     */
    private suspend fun governingClientCapabilities(): ClientCapabilities? =
        currentRequestHandlerExtra()?.clientCapabilities ?: session.clientCapabilities

    /**
     * Refuses a server-initiated request on a connection whose revision has none.
     *
     * Protocol revision `2026-07-28` removed sampling, elicitation and roots as server-initiated
     * requests. A server that needs them must be reached over `2025-11-25` or earlier.
     */
    private suspend fun requireBackChannel(method: Method) {
        val extra = currentRequestHandlerExtra() ?: return
        if (extra.envelope == null) return
        throw McpException(
            code = RPCError.ErrorCode.METHOD_NOT_FOUND,
            message = "${method.value} was removed in protocol version ${extra.protocolVersion}: " +
                "server-initiated requests are replaced by multi-round-trip requests, which this " +
                "SDK does not implement yet. Serve peers that need it over 2025-11-25 or earlier.",
        )
    }

    /**
     * Demands [required] of the client governing this request.
     *
     * @throws McpException [RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY], carrying the
     * capabilities the declaration did not cover, when it does not cover them all
     */
    private suspend fun requireClientCapabilities(required: ClientCapabilities) {
        val missing = required.missingIn(governingClientCapabilities()) ?: return
        throw McpException(
            code = RPCError.ErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY,
            message = "Client did not declare the capabilities required to serve this request",
            data = McpJson.encodeToJsonElement(MissingRequiredClientCapabilityData(missing)),
        )
    }
}
