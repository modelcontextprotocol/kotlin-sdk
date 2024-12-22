package io.modelcontextprotocol.kotlin.sdk.client

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.LoggingMessageNotification.SetLevelRequest
import io.modelcontextprotocol.kotlin.sdk.shared.Protocol
import io.modelcontextprotocol.kotlin.sdk.shared.ProtocolOptions
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Options for configuring the MCP client.
 *
 * @property capabilities The capabilities this client supports.
 * @property enforceStrictCapabilities Whether to strictly enforce capabilities when interacting with the server.
 */
public class ClientOptions(
    public val capabilities: ClientCapabilities = ClientCapabilities(),
    enforceStrictCapabilities: Boolean = true,
) : ProtocolOptions(enforceStrictCapabilities = enforceStrictCapabilities)

/**
 * An MCP client on top of a pluggable transport.
 *
 * The client automatically performs the initialization handshake with the server when [connect] is called.
 * After initialization, [getServerCapabilities] and [getServerVersion] provide details about the connected server.
 *
 * You can extend this class with custom request/notification/result types if needed.
 *
 * @param clientInfo Information about the client implementation (name, version).
 * @param options Configuration options for this client.
 */
public open class Client(
    private val clientInfo: Implementation,
    options: ClientOptions = ClientOptions(),
) : Protocol(options) {

    private var serverCapabilities: ServerCapabilities? = null
    private var serverVersion: Implementation? = null
    private val capabilities: ClientCapabilities = options.capabilities

    protected fun assertCapability(capability: String, method: String) {
        val caps = serverCapabilities
        val hasCapability = when (capability) {
            "logging" -> caps?.logging != null
            "prompts" -> caps?.prompts != null
            "resources" -> caps?.resources != null
            "tools" -> caps?.tools != null
            else -> true
        }

        if (!hasCapability) {
            throw IllegalStateException("Server does not support $capability (required for $method)")
        }
    }

    /**
     * Connects the client to the given [transport], performing the initialization handshake with the server.
     *
     * @param transport The transport to use for communication with the server.
     * @throws IllegalStateException If the server's protocol version is not supported.
     */
    override suspend fun connect(transport: Transport) {
        super.connect(transport)

        try {
            val message = InitializeRequest(
                protocolVersion = LATEST_PROTOCOL_VERSION,
                capabilities = capabilities,
                clientInfo = clientInfo
            )
            val result = request<InitializeResult>(message)

            if (!SUPPORTED_PROTOCOL_VERSIONS.contains(result.protocolVersion)) {
                throw IllegalStateException(
                    "Server's protocol version is not supported: ${result.protocolVersion}"
                )
            }

            serverCapabilities = result.capabilities
            serverVersion = result.serverInfo

            notification(InitializedNotification())
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    /**
     * Retrieves the server's reported capabilities after the initialization process completes.
     *
     * @return The server's capabilities, or `null` if initialization is not yet complete.
     */
    public fun getServerCapabilities(): ServerCapabilities? {
        return serverCapabilities
    }

    /**
     * Retrieves the server's reported version information after initialization.
     *
     * @return Information about the server's implementation, or `null` if initialization is not yet complete.
     */
    public fun getServerVersion(): Implementation? {
        return serverVersion
    }

    override fun assertCapabilityForMethod(method: Method) {
        when (method) {
            Method.Defined.LoggingSetLevel -> {
                if (serverCapabilities?.logging == null) {
                    throw IllegalStateException("Server does not support logging (required for $method)")
                }
            }

            Method.Defined.PromptsGet,
            Method.Defined.PromptsList,
            Method.Defined.CompletionComplete,
                -> {
                if (serverCapabilities?.prompts == null) {
                    throw IllegalStateException("Server does not support prompts (required for $method)")
                }
            }

            Method.Defined.ResourcesList,
            Method.Defined.ResourcesTemplatesList,
            Method.Defined.ResourcesRead,
            Method.Defined.ResourcesSubscribe,
            Method.Defined.ResourcesUnsubscribe,
                -> {
                val resCaps = serverCapabilities?.resources
                if (resCaps == null) {
                    throw IllegalStateException("Server does not support resources (required for $method)")
                }

                if (method == Method.Defined.ResourcesSubscribe && resCaps.subscribe != true) {
                    throw IllegalStateException(
                        "Server does not support resource subscriptions (required for $method)"
                    )
                }
            }

            Method.Defined.ToolsCall,
            Method.Defined.ToolsList,
                -> {
                if (serverCapabilities?.tools == null) {
                    throw IllegalStateException("Server does not support tools (required for $method)")
                }
            }

            Method.Defined.Initialize,
            Method.Defined.Ping,
                -> {
                // No specific capability required
            }

            else -> {
                // For unknown or future methods, no assertion by default
            }
        }
    }

    override fun assertNotificationCapability(method: Method) {
        when (method) {
            Method.Defined.NotificationsRootsListChanged -> {
                if (capabilities.roots?.listChanged != true) {
                    throw IllegalStateException(
                        "Client does not support roots list changed notifications (required for $method)"
                    )
                }
            }

            Method.Defined.NotificationsInitialized,
            Method.Defined.NotificationsCancelled,
            Method.Defined.NotificationsProgress,
                -> {
                // Always allowed
            }

            else -> {
                // For notifications not specifically listed, no assertion by default
            }
        }
    }

    override fun assertRequestHandlerCapability(method: Method) {
        when (method) {
            Method.Defined.SamplingCreateMessage -> {
                if (capabilities.sampling == null) {
                    throw IllegalStateException(
                        "Client does not support sampling capability (required for $method)"
                    )
                }
            }

            Method.Defined.RootsList -> {
                if (capabilities.roots == null) {
                    throw IllegalStateException(
                        "Client does not support roots capability (required for $method)"
                    )
                }
            }

            Method.Defined.Ping -> {
                // No capability required
            }

            else -> {}
        }
    }


    /**
     * Sends a ping request to the server to check connectivity.
     *
     * @param options Optional request options.
     * @throws IllegalStateException If the server does not support the ping method (unlikely).
     */
    public suspend fun ping(options: RequestOptions? = null): EmptyRequestResult {
        return request<EmptyRequestResult>(PingRequest(), options)
    }

    /**
     * Sends a completion request to the server, typically to generate or complete some content.
     *
     * @param params The completion request parameters.
     * @param options Optional request options.
     * @return The completion result returned by the server, or `null` if none.
     * @throws IllegalStateException If the server does not support prompts or completion.
     */
    public suspend fun complete(params: CompleteRequest, options: RequestOptions? = null): CompleteResult? {
        return request<CompleteResult>(params, options)
    }

    /**
     * Sets the logging level on the server.
     *
     * @param level The desired logging level.
     * @param options Optional request options.
     * @throws IllegalStateException If the server does not support logging.
     */
    public suspend fun setLoggingLevel(level: LoggingLevel, options: RequestOptions? = null): EmptyRequestResult {
        return request<EmptyRequestResult>(SetLevelRequest(level), options)
    }

    /**
     * Retrieves a prompt by name from the server.
     *
     * @param request The prompt request containing the prompt name.
     * @param options Optional request options.
     * @return The requested prompt details, or `null` if not found.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public suspend fun getPrompt(request: GetPromptRequest, options: RequestOptions? = null): GetPromptResult? {
        return request<GetPromptResult>(request, options)
    }

    /**
     * Lists all available prompts from the server.
     *
     * @param request A request object for listing prompts (usually empty).
     * @param options Optional request options.
     * @return The list of available prompts, or `null` if none.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public suspend fun listPrompts(
        request: ListPromptsRequest = ListPromptsRequest(),
        options: RequestOptions? = null,
    ): ListPromptsResult? {
        return request<ListPromptsResult>(request, options)
    }

    /**
     * Lists all available resources from the server.
     *
     * @param request A request object for listing resources (usually empty).
     * @param options Optional request options.
     * @return The list of resources, or `null` if none.
     * @throws IllegalStateException If the server does not support resources.
     */
    public suspend fun listResources(
        request: ListResourcesRequest = ListResourcesRequest(),
        options: RequestOptions? = null,
    ): ListResourcesResult? {
        return request<ListResourcesResult>(request, options)
    }

    /**
     * Lists resource templates available on the server.
     *
     * @param request The request object for listing resource templates.
     * @param options Optional request options.
     * @return The list of resource templates, or `null` if none.
     * @throws IllegalStateException If the server does not support resources.
     */
    public suspend fun listResourceTemplates(
        request: ListResourceTemplatesRequest,
        options: RequestOptions? = null,
    ): ListResourceTemplatesResult? {
        return request<ListResourceTemplatesResult>(request, options)
    }

    /**
     * Reads a resource from the server by its URI.
     *
     * @param request The request object containing the resource URI.
     * @param options Optional request options.
     * @return The resource content, or `null` if the resource is not found.
     * @throws IllegalStateException If the server does not support resources.
     */
    public suspend fun readResource(
        request: ReadResourceRequest,
        options: RequestOptions? = null,
    ): ReadResourceResult? {
        return request<ReadResourceResult>(request, options)
    }

    /**
     * Subscribes to resource changes on the server.
     *
     * @param request The subscription request containing resource details.
     * @param options Optional request options.
     * @throws IllegalStateException If the server does not support resource subscriptions.
     */
    public suspend fun subscribeResource(
        request: SubscribeRequest,
        options: RequestOptions? = null,
    ): EmptyRequestResult {
        return request<EmptyRequestResult>(request, options)
    }

    /**
     * Unsubscribes from resource changes on the server.
     *
     * @param request The unsubscribe request containing resource details.
     * @param options Optional request options.
     * @throws IllegalStateException If the server does not support resource subscriptions.
     */
    public suspend fun unsubscribeResource(
        request: UnsubscribeRequest,
        options: RequestOptions? = null,
    ): EmptyRequestResult {
        return request<EmptyRequestResult>(request, options)
    }

    /**
     * Calls a tool on the server by name, passing the specified arguments.
     *
     * @param name The name of the tool to call.
     * @param arguments A map of argument names to values for the tool.
     * @param compatibility Whether to use compatibility mode for older protocol versions.
     * @param options Optional request options.
     * @return The result of the tool call, or `null` if none.
     * @throws IllegalStateException If the server does not support tools.
     */
    public suspend fun callTool(
        name: String,
        arguments: Map<String, Any?>,
        compatibility: Boolean = false,
        options: RequestOptions? = null,
    ): CallToolResultBase? {
        val jsonArguments = arguments.mapValues { (_, value) ->
            when (value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is JsonElement -> value
                null -> JsonNull
                else -> JsonPrimitive(value.toString())
            }
        }

        val request = CallToolRequest(
            name = name,
            arguments = JsonObject(jsonArguments)
        )
        return callTool(request, compatibility, options)
    }

    /**
     * Calls a tool on the server using a [CallToolRequest] object.
     *
     * @param request The request object containing the tool name and arguments.
     * @param compatibility Whether to use compatibility mode for older protocol versions.
     * @param options Optional request options.
     * @return The result of the tool call, or `null` if none.
     * @throws IllegalStateException If the server does not support tools.
     */
    public suspend fun callTool(
        request: CallToolRequest,
        compatibility: Boolean = false,
        options: RequestOptions? = null,
    ): CallToolResultBase? {
        return if (compatibility) {
            request<CompatibilityCallToolResult>(request, options)
        } else {
            request<CallToolResult>(request, options)
        }
    }

    /**
     * Lists all available tools on the server.
     *
     * @param request A request object for listing tools (usually empty).
     * @param options Optional request options.
     * @return The list of available tools, or `null` if none.
     * @throws IllegalStateException If the server does not support tools.
     */
    public suspend fun listTools(
        request: ListToolsRequest = ListToolsRequest(),
        options: RequestOptions? = null,
    ): ListToolsResult? {
        return request<ListToolsResult>(request, options)
    }

    /**
     * Notifies the server that the list of roots has changed.
     * Typically used if the client is managing some form of hierarchical structure.
     *
     * @throws IllegalStateException If the client or server does not support roots.
     */
    public suspend fun sendRootsListChanged() {
        notification(RootsListChangedNotification())
    }
}
