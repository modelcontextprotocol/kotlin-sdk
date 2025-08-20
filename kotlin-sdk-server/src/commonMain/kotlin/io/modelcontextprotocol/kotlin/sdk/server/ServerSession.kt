package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.CreateElicitationRequest
import io.modelcontextprotocol.kotlin.sdk.CreateElicitationRequest.RequestedSchema
import io.modelcontextprotocol.kotlin.sdk.CreateElicitationResult
import io.modelcontextprotocol.kotlin.sdk.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.EmptyRequestResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.Method.Defined
import io.modelcontextprotocol.kotlin.sdk.PingRequest
import io.modelcontextprotocol.kotlin.sdk.PromptListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.ResourceListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.SUPPORTED_PROTOCOL_VERSIONS
import io.modelcontextprotocol.kotlin.sdk.ToolListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.shared.Protocol
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger {}

public open class ServerSession(private val serverInfo: Implementation, options: ServerOptions) : Protocol(options) {
    @Suppress("ktlint:standard:backing-property-naming")
    private var _onInitialized: (() -> Unit) = {}

    @Suppress("ktlint:standard:backing-property-naming")
    private var _onClose: () -> Unit = {}

    init {
        // Core protocol handlers
        setRequestHandler<InitializeRequest>(Method.Defined.Initialize) { request, _ ->
            handleInitialize(request)
        }
        setNotificationHandler<InitializedNotification>(Method.Defined.NotificationsInitialized) {
            _onInitialized()
            CompletableDeferred(Unit)
        }
    }

    /**
     * The capabilities supported by the server, related to the session.
     */
    private val serverCapabilities = options.capabilities

    /**
     * The client's reported capabilities after initialization.
     */
    public var clientCapabilities: ClientCapabilities? = null
        private set

    /**
     * The client's version information after initialization.
     */
    public var clientVersion: Implementation? = null
        private set

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
     * Sends a ping request to the client to check connectivity.
     *
     * @return The result of the ping request.
     * @throws IllegalStateException If for some reason the method is not supported or the connection is closed.
     */
    public suspend fun ping(): EmptyRequestResult = request<EmptyRequestResult>(PingRequest())

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
        logger.debug { "Creating message with params: $params" }
        return request<CreateMessageResult>(params, options)
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
        return request<ListRootsResult>(ListRootsRequest(params), options)
    }

    public suspend fun createElicitation(
        message: String,
        requestedSchema: RequestedSchema,
        options: RequestOptions? = null,
    ): CreateElicitationResult {
        logger.debug { "Creating elicitation with message: $message" }
        return request(CreateElicitationRequest(message, requestedSchema), options)
    }

    /**
     * Sends a logging message notification to the client.
     *
     * @param notification The logging message notification.
     */
    public suspend fun sendLoggingMessage(notification: LoggingMessageNotification) {
        logger.trace { "Sending logging message: ${notification.params.data}" }
        notification(notification)
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
                    throw IllegalStateException("Client does not support sampling (required for ${method.value})")
                }
            }

            Defined.RootsList -> {
                if (clientCapabilities?.roots == null) {
                    throw IllegalStateException("Client does not support listing roots (required for ${method.value})")
                }
            }

            Defined.ElicitationCreate -> {
                if (clientCapabilities?.elicitation == null) {
                    throw IllegalStateException("Client does not support elicitation (required for ${method.value})")
                }
            }

            Defined.Ping -> {
                // No specific capability required
            }

            else -> {
                throw IllegalStateException("Server does not support $method")
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
                    throw IllegalStateException("Server does not support logging (required for ${method.value})")
                }
            }

            Defined.NotificationsResourcesUpdated,
            Defined.NotificationsResourcesListChanged,
            -> {
                if (serverCapabilities.resources == null) {
                    throw IllegalStateException(
                        "Server does not support notifying about resources (required for ${method.value})",
                    )
                }
            }

            Defined.NotificationsResourcesListChanged -> {
                if (serverCapabilities.tools == null) {
                    throw IllegalStateException(
                        "Server does not support notifying of tool list changes (required for ${method.value})",
                    )
                }
            }

            Defined.NotificationsPromptsListChanged -> {
                if (serverCapabilities.prompts == null) {
                    throw IllegalStateException(
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
                throw IllegalStateException("Server does not support $method or it's not a notification method")
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
                if (serverCapabilities.sampling == null) {
                    logger.error { "Server capability assertion failed: sampling not supported" }
                    throw IllegalStateException("Server does not support sampling (required for $method)")
                }
            }

            Defined.LoggingSetLevel -> {
                if (serverCapabilities.logging == null) {
                    throw IllegalStateException("Server does not support logging (required for $method)")
                }
            }

            Defined.PromptsGet,
            Defined.PromptsList,
            -> {
                if (serverCapabilities.prompts == null) {
                    throw IllegalStateException("Server does not support prompts (required for $method)")
                }
            }

            Defined.ResourcesList,
            Defined.ResourcesTemplatesList,
            Defined.ResourcesRead,
            Defined.ResourcesSubscribe,
            Defined.ResourcesUnsubscribe,
            -> {
                if (serverCapabilities.resources == null) {
                    throw IllegalStateException("Server does not support resources (required for $method)")
                }
            }

            Defined.ToolsCall,
            Defined.ToolsList,
            -> {
                if (serverCapabilities.tools == null) {
                    throw IllegalStateException("Server does not support tools (required for $method)")
                }
            }

            Defined.Ping, Defined.Initialize -> {
                // No capability required
            }

            else -> {
                throw IllegalStateException("Server does not support $method or it's not a request handler method")
            }
        }
    }

    private suspend fun handleInitialize(request: InitializeRequest): InitializeResult {
        logger.debug { "Handling initialization request from client" }
        clientCapabilities = request.capabilities
        clientVersion = request.clientInfo

        val requestedVersion = request.protocolVersion
        val protocolVersion = if (SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion)) {
            requestedVersion
        } else {
            logger.warn {
                "Client requested unsupported protocol version $requestedVersion, falling back to $LATEST_PROTOCOL_VERSION"
            }
            LATEST_PROTOCOL_VERSION
        }

        return InitializeResult(
            protocolVersion = protocolVersion,
            capabilities = serverCapabilities,
            serverInfo = serverInfo,
        )
    }
}
