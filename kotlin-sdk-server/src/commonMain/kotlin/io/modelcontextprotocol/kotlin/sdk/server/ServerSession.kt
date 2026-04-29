package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.shared.Protocol
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.BaseRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitationCompleteNotification
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Method.Defined
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.SUPPORTED_PROTOCOL_VERSIONS
import io.modelcontextprotocol.kotlin.sdk.types.SetLevelRequest
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

/**
 * Represents an active server-side session in the Model Context Protocol.
 *
 * Owns the server side of a single client connection on top of [Protocol]. Drives the
 * initialize/initialized handshake, tracks the negotiated client capabilities and version,
 * asserts capability requirements before dispatching requests and notifications, and exposes
 * server-to-client operations such as sampling, root listing, elicitation, logging, and
 * list-change notifications.
 *
 * Equality and hashing are based on [sessionId], so a session can be used as a key in
 * routing structures.
 *
 * @property serverInfo server name and version reported to the client during initialization
 * @param options server capabilities and protocol options used by [Protocol]
 * @property instructions optional human-readable instructions for the client, sent in the initialize result
 */
public open class ServerSession(
    protected val serverInfo: Implementation,
    options: ServerOptions,
    protected val instructions: String?,
) : Protocol(options) {

    /** Unique identifier for this session, generated on creation. */
    @OptIn(ExperimentalUuidApi::class)
    public val sessionId: String = Uuid.random().toString()

    private var _onInitialized: (() -> Unit) = {}

    private var _onClose: () -> Unit = {}

    private val _clientCapabilities: AtomicRef<ClientCapabilities?> = atomic(null)
    private val _clientVersion: AtomicRef<Implementation?> = atomic(null)

    /** Capabilities reported by the client during initialization, or `null` before the handshake completes. */
    public val clientCapabilities: ClientCapabilities? get() = _clientCapabilities.value

    /** Client implementation information reported during initialization, or `null` before the handshake completes. */
    public val clientVersion: Implementation? get() = _clientVersion.value

    /**
     * The capabilities supported by the server, related to the session.
     */
    private val serverCapabilities = options.capabilities

    /**
     * The current logging level set by the client.
     * When null, all messages are sent (no filtering).
     */
    internal val currentLoggingLevel: AtomicRef<LoggingLevel?> = atomic(null)

    init {
        // Core protocol handlers
        setRequestHandler<InitializeRequest>(Defined.Initialize) { request, _ ->
            handleInitialize(request)
        }
        setNotificationHandler<InitializedNotification>(Defined.NotificationsInitialized) {
            _onInitialized()
            CompletableDeferred(Unit)
        }

        // Logging level handler
        if (options.capabilities.logging != null) {
            setRequestHandler<SetLevelRequest>(Defined.LoggingSetLevel) { request, _ ->
                currentLoggingLevel.value = request.params.level
                logger.debug { "Logging level set to: ${request.params.level}" }
                EmptyResult()
            }
        }
    }

    internal val clientConnection: ClientConnection = ClientConnectionImpl(this)

    /**
     * Registers a callback to be invoked when the server has completed initialization.
     */
    public fun onInitialized(block: () -> Unit) {
        val old = _onInitialized
        _onInitialized = {
            old()
            block()
        }
    }

    /**
     * Registers a callback to be invoked when the server session is closing.
     */
    public fun onClose(block: () -> Unit) {
        val old = _onClose
        _onClose = {
            old()
            block()
        }
    }

    /**
     * Called when the server session is closing.
     */
    override fun onClose() {
        logger.debug { "Server connection closing" }
        _onClose()
    }

    /**
     * Asserts that the client supports the capability required for the given [method].
     *
     * This method is automatically called by the [Protocol] framework before handling requests.
     * Throws [IllegalStateException] if the capability is not supported.
     *
     * @param method The method for which we are asserting capability.
     */
    override fun assertCapabilityForMethod(method: Method) {
        logger.trace { "Asserting capability for method: ${method.value}" }
        when (method) {
            Defined.SamplingCreateMessage -> {
                if (clientCapabilities?.sampling == null) {
                    logger.error { "Client capability assertion failed: sampling not supported" }
                    error("Client does not support sampling (required for ${method.value})")
                }
            }

            Defined.RootsList -> {
                if (clientCapabilities?.roots == null) {
                    logger.error { "Client capability assertion failed: listing roots not supported" }
                    error("Client does not support listing roots (required for ${method.value})")
                }
            }

            Defined.ElicitationCreate -> {
                if (clientCapabilities?.elicitation == null) {
                    logger.error { "Client capability assertion failed: elicitation not supported" }
                    error("Client does not support elicitation (required for ${method.value})")
                }
            }

            Defined.Ping -> {
                // No specific capability required
            }

            else -> {
                // For notifications not specifically listed, no assertion by default
            }
        }
    }

    /**
     * Asserts that the server can handle the specified notification method.
     *
     * Throws [IllegalStateException] if the server does not have the capabilities required to handle this notification.
     *
     * @param method The notification method.
     */
    override fun assertNotificationCapability(method: Method) {
        logger.trace { "Asserting notification capability for method: ${method.value}" }
        when (method) {
            Defined.NotificationsMessage -> {
                if (serverCapabilities.logging == null) {
                    logger.error { "Server capability assertion failed: logging not supported" }
                    error("Server does not support logging (required for ${method.value})")
                }
            }

            Defined.NotificationsResourcesUpdated,
            Defined.NotificationsResourcesListChanged,
            -> {
                if (serverCapabilities.resources == null) {
                    error(
                        "Server does not support notifying about resources (required for ${method.value})",
                    )
                }
            }

            Defined.NotificationsToolsListChanged -> {
                if (serverCapabilities.tools == null) {
                    error(
                        "Server does not support notifying of tool list changes (required for ${method.value})",
                    )
                }
            }

            Defined.NotificationsPromptsListChanged -> {
                if (serverCapabilities.prompts == null) {
                    error(
                        "Server does not support notifying of prompt list changes (required for ${method.value})",
                    )
                }
            }

            Defined.NotificationsCancelled,
            Defined.NotificationsProgress,
            -> {
                // Always allowed
            }

            else -> {
                // For notifications not specifically listed, no assertion by default
            }
        }
    }

    /**
     * Asserts that the server can handle the specified request method.
     *
     * Throws [IllegalStateException] if the server does not have the capabilities required to handle this request.
     *
     * @param method The request method.
     */
    override fun assertRequestHandlerCapability(method: Method) {
        logger.trace { "Asserting request handler capability for method: ${method.value}" }
        when (method) {
            Defined.SamplingCreateMessage -> {
                if (serverCapabilities.experimental?.get("sampling") == null) {
                    logger.error { "Server capability assertion failed: sampling not supported" }
                    error("Server does not support sampling (required for $method)")
                }
            }

            Defined.LoggingSetLevel -> {
                if (serverCapabilities.logging == null) {
                    logger.error { "Server does not support logging (required for $method)" }
                    error("Server does not support logging (required for $method)")
                }
            }

            Defined.PromptsGet,
            Defined.PromptsList,
            -> {
                if (serverCapabilities.prompts == null) {
                    error("Server does not support prompts (required for $method)")
                }
            }

            Defined.ResourcesList,
            Defined.ResourcesTemplatesList,
            Defined.ResourcesRead,
            Defined.ResourcesSubscribe,
            Defined.ResourcesUnsubscribe,
            -> {
                if (serverCapabilities.resources == null) {
                    error("Server does not support resources (required for $method)")
                }
            }

            Defined.ToolsCall,
            Defined.ToolsList,
            -> {
                if (serverCapabilities.tools == null) {
                    error("Server does not support tools (required for $method)")
                }
            }

            Defined.Ping, Defined.Initialize -> {
                // No capability required
            }

            else -> {
                // For notifications not specifically listed, no assertion by default
            }
        }
    }

    private fun handleInitialize(request: InitializeRequest): InitializeResult {
        if (!_clientCapabilities.compareAndSet(null, request.params.capabilities)) {
            throw McpException(
                code = RPCError.ErrorCode.INVALID_REQUEST,
                message = "Server already initialized",
            )
        }

        logger.debug { "Handling initialization request from client" }
        _clientVersion.value = request.params.clientInfo

        val requestedVersion = request.params.protocolVersion
        val protocolVersion = if (SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion)) {
            requestedVersion
        } else {
            logger.warn {
                "Client requested unsupported protocol version $requestedVersion, " +
                    "falling back to $LATEST_PROTOCOL_VERSION"
            }
            LATEST_PROTOCOL_VERSION
        }

        return InitializeResult(
            protocolVersion = protocolVersion,
            capabilities = serverCapabilities,
            serverInfo = serverInfo,
            instructions = instructions,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ServerSession) return false
        return sessionId == other.sessionId
    }

    override fun hashCode(): Int = sessionId.hashCode()

    // Start the ClientConnection redirection section

    /**
     * Sends a ping request to the client to check connectivity.
     *
     * @return The result of the ping request.
     * @throws IllegalStateException If for some reason the method is not supported or the connection is closed.
     */
    public suspend fun ping(): EmptyResult = clientConnection.ping()

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
    ): CreateMessageResult = clientConnection.createMessage(params, options)

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
    ): ElicitResult = clientConnection.createElicitation(message, requestedSchema, options)

    /**
     * Sends a URL mode elicitation request to the client, directing the user
     * to an external URL for out-of-band interactions.
     *
     * @param message The message explaining why the interaction is needed.
     * @param elicitationId A unique identifier for the elicitation.
     * @param url The URL that the user should navigate to.
     * @param options Optional request options.
     * @return The result of the elicitation request.
     * @throws IllegalStateException If the server or client does not support elicitation.
     */
    public suspend fun createElicitation(
        message: String,
        elicitationId: String,
        url: String,
        options: RequestOptions? = null,
    ): ElicitResult = clientConnection.createElicitation(message, elicitationId, url, options)

    /**
     * Sends a logging message notification to the client.
     * Messages are filtered based on the current logging level set by the client.
     * If no logging level is set, all messages are sent.
     *
     * @param notification The logging message notification.
     */
    public suspend fun sendLoggingMessage(notification: LoggingMessageNotification): Unit =
        clientConnection.sendLoggingMessage(notification)

    /**
     * Sends a resource-updated notification to the client, indicating that a specific resource has changed.
     *
     * @param notification Details of the updated resource.
     */
    public suspend fun sendResourceUpdated(notification: ResourceUpdatedNotification): Unit =
        clientConnection.sendResourceUpdated(notification)

    /**
     * Sends a notification to the client indicating that the list of resources has changed.
     */
    public suspend fun sendResourceListChanged(): Unit = clientConnection.sendResourceListChanged()

    /**
     * Sends a notification to the client indicating that the list of tools has changed.
     */
    public suspend fun sendToolListChanged(): Unit = clientConnection.sendToolListChanged()

    /**
     * Sends a notification to the client indicating that the list of prompts has changed.
     */
    public suspend fun sendPromptListChanged(): Unit = clientConnection.sendPromptListChanged()

    /**
     * Sends a notification to the client indicating that an out-of-band elicitation has completed.
     *
     * @param notification Details of the completed elicitation.
     */
    public suspend fun sendElicitationComplete(notification: ElicitationCompleteNotification): Unit =
        clientConnection.sendElicitationComplete(notification)
    // End the ClientConnection redirection section
}
