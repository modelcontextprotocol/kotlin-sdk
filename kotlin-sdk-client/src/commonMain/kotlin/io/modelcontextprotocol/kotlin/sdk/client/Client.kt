package io.modelcontextprotocol.kotlin.sdk.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.CallToolResultBase
import io.modelcontextprotocol.kotlin.sdk.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.CompatibilityCallToolResult
import io.modelcontextprotocol.kotlin.sdk.CompleteRequest
import io.modelcontextprotocol.kotlin.sdk.CompleteResult
import io.modelcontextprotocol.kotlin.sdk.CreateElicitationRequest
import io.modelcontextprotocol.kotlin.sdk.CreateElicitationResult
import io.modelcontextprotocol.kotlin.sdk.EmptyRequestResult
import io.modelcontextprotocol.kotlin.sdk.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.ListPromptsRequest
import io.modelcontextprotocol.kotlin.sdk.ListPromptsResult
import io.modelcontextprotocol.kotlin.sdk.ListResourceTemplatesRequest
import io.modelcontextprotocol.kotlin.sdk.ListResourceTemplatesResult
import io.modelcontextprotocol.kotlin.sdk.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.ListResourcesResult
import io.modelcontextprotocol.kotlin.sdk.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.LoggingMessageNotification.SetLevelRequest
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.PingRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.Root
import io.modelcontextprotocol.kotlin.sdk.RootsListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.SUPPORTED_PROTOCOL_VERSIONS
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.SubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.UnsubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.shared.Protocol
import io.modelcontextprotocol.kotlin.sdk.shared.ProtocolOptions
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.coroutines.cancellation.CancellationException

private val logger = KotlinLogging.logger {}

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
 * After initialization, [serverCapabilities] and [serverVersion] provide details about the connected server.
 *
 * You can extend this class with custom request/notification/result types if needed.
 *
 * @param clientInfo Information about the client implementation (name, version).
 * @param options Configuration options for this client.
 */
public open class Client(private val clientInfo: Implementation, options: ClientOptions = ClientOptions()) :
    Protocol(options) {

    /**
     * Retrieves the server's reported capabilities after the initialization process completes.
     *
     * @return The server's capabilities, or `null` if initialization is not yet complete.
     */
    public var serverCapabilities: ServerCapabilities? = null
        private set

    /**
     * Optional human-readable instructions or description from the server.
     *
     * @return Instructions provided by the server, or `null` if none were given or initialization is not yet complete.
     */
    public var serverInstructions: String? = null
        private set

    /**
     * Retrieves the server's reported version information after initialization.
     *
     * @return Information about the server's implementation, or `null` if initialization is not yet complete.
     */
    public var serverVersion: Implementation? = null
        private set

    private val capabilities: ClientCapabilities = options.capabilities

    private val roots = atomic(persistentMapOf<String, Root>())

    init {
        logger.debug { "Initializing MCP client with capabilities: $capabilities" }

        // Internal handlers for roots
        if (capabilities.roots != null) {
            setRequestHandler<ListRootsRequest>(Method.Defined.RootsList) { _, _ ->
                handleListRoots()
            }
        }
    }

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
                clientInfo = clientInfo,
            )
            val result = request<InitializeResult>(message)

            if (!SUPPORTED_PROTOCOL_VERSIONS.contains(result.protocolVersion)) {
                throw IllegalStateException(
                    "Server's protocol version is not supported: ${result.protocolVersion}",
                )
            }

            serverCapabilities = result.capabilities
            serverVersion = result.serverInfo
            serverInstructions = result.instructions

            notification(InitializedNotification())
        } catch (error: Throwable) {
            logger.error(error) { "Failed to initialize client: ${error.message}" }
            close()

            if (error !is CancellationException) {
                throw IllegalStateException("Error connecting to transport: ${error.message}", error)
            }

            throw error
        }
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
                    ?: error("Server does not support resources (required for $method)")

                if (method == Method.Defined.ResourcesSubscribe && resCaps.subscribe != true) {
                    throw IllegalStateException(
                        "Server does not support resource subscriptions (required for $method)",
                    )
                }
            }

            Method.Defined.ToolsCall, Method.Defined.ToolsList -> {
                if (serverCapabilities?.tools == null) {
                    throw IllegalStateException("Server does not support tools (required for $method)")
                }
            }

            Method.Defined.Initialize, Method.Defined.Ping -> {
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
                        "Client does not support roots list changed notifications (required for $method)",
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
                        "Client does not support sampling capability (required for $method)",
                    )
                }
            }

            Method.Defined.RootsList -> {
                if (capabilities.roots == null) {
                    throw IllegalStateException(
                        "Client does not support roots capability (required for $method)",
                    )
                }
            }

            Method.Defined.ElicitationCreate -> {
                if (capabilities.elicitation == null) {
                    throw IllegalStateException(
                        "Client does not support elicitation capability (required for $method)",
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
    public suspend fun ping(options: RequestOptions? = null): EmptyRequestResult =
        request<EmptyRequestResult>(PingRequest(), options)

    /**
     * Sends a completion request to the server, typically to generate or complete some content.
     *
     * @param params The completion request parameters.
     * @param options Optional request options.
     * @return The completion result returned by the server, or `null` if none.
     * @throws IllegalStateException If the server does not support prompts or completion.
     */
    public suspend fun complete(params: CompleteRequest, options: RequestOptions? = null): CompleteResult =
        request(params, options)

    /**
     * Sets the logging level on the server.
     *
     * @param level The desired logging level.
     * @param options Optional request options.
     * @throws IllegalStateException If the server does not support logging.
     */
    public suspend fun setLoggingLevel(level: LoggingLevel, options: RequestOptions? = null): EmptyRequestResult =
        request<EmptyRequestResult>(SetLevelRequest(level), options)

    /**
     * Retrieves a prompt by name from the server.
     *
     * @param request The prompt request containing the prompt name.
     * @param options Optional request options.
     * @return The requested prompt details, or `null` if not found.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public suspend fun getPrompt(request: GetPromptRequest, options: RequestOptions? = null): GetPromptResult =
        request(request, options)

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
    ): ListPromptsResult = request(request, options)

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
    ): ListResourcesResult = request(request, options)

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
    ): ListResourceTemplatesResult = request(request, options)

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
    ): ReadResourceResult = request(request, options)

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
    ): EmptyRequestResult = request(request, options)

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
    ): EmptyRequestResult = request(request, options)

    /**
     * Calls a tool on the server by name, passing the specified arguments and metadata.
     *
     * @param name The name of the tool to call.
     * @param arguments A map of argument names to values for the tool.
     * @param meta A map of metadata key-value pairs. Keys must follow MCP specification format.
     *             - Optional prefix: dot-separated labels followed by slash (e.g., "api.example.com/")
     *             - Name: alphanumeric start/end, may contain hyphens, underscores, dots, alphanumerics
     *             - Reserved prefixes starting with "mcp" or "modelcontextprotocol" are forbidden
     * @param compatibility Whether to use compatibility mode for older protocol versions.
     * @param options Optional request options.
     * @return The result of the tool call, or `null` if none.
     * @throws IllegalStateException If the server does not support tools.
     */
    public suspend fun callTool(
        name: String,
        arguments: Map<String, Any?>,
        meta: Map<String, Any?> = emptyMap(),
        compatibility: Boolean = false,
        options: RequestOptions? = null,
    ): CallToolResultBase? {
        validateMetaKeys(meta.keys)

        val jsonArguments = convertToJsonMap(arguments)
        val jsonMeta = convertToJsonMap(meta)

        val request = CallToolRequest(
            name = name,
            arguments = JsonObject(jsonArguments),
            _meta = JsonObject(jsonMeta),
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
    ): CallToolResultBase? = if (compatibility) {
        request<CompatibilityCallToolResult>(request, options)
    } else {
        request<CallToolResult>(request, options)
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
    ): ListToolsResult = request(request, options)

    /**
     * Registers a single root.
     *
     * @param uri The URI of the root.
     * @param name A human-readable name for the root.
     * @throws IllegalStateException If the client does not support roots.
     */
    public fun addRoot(uri: String, name: String) {
        if (capabilities.roots == null) {
            logger.error { "Failed to add root '$name': Client does not support roots capability" }
            throw IllegalStateException("Client does not support roots capability.")
        }
        logger.info { "Adding root: $name ($uri)" }
        roots.update { current -> current.put(uri, Root(uri, name)) }
    }

    /**
     * Registers multiple roots at once.
     *
     * @param rootsToAdd A list of [Root] objects to register.
     * @throws IllegalStateException If the client does not support roots.
     */
    public fun addRoots(rootsToAdd: List<Root>) {
        if (capabilities.roots == null) {
            logger.error { "Failed to add roots: Client does not support roots capability" }
            throw IllegalStateException("Client does not support roots capability.")
        }
        logger.info { "Adding ${rootsToAdd.size} roots" }
        roots.update { current -> current.putAll(rootsToAdd.associateBy { it.uri }) }
    }

    /**
     * Removes a single root by URI.
     *
     * @param uri The URI of the root to remove.
     * @return True if the root was removed, false if it wasn't found.
     * @throws IllegalStateException If the client does not support roots.
     */
    public fun removeRoot(uri: String): Boolean {
        if (capabilities.roots == null) {
            logger.error { "Failed to remove root '$uri': Client does not support roots capability" }
            throw IllegalStateException("Client does not support roots capability.")
        }
        logger.info { "Removing root: $uri" }
        val oldMap = roots.getAndUpdate { current -> current.remove(uri) }
        val removed = uri in oldMap
        logger.debug {
            if (removed) {
                "Root removed: $uri"
            } else {
                "Root not found: $uri"
            }
        }
        return removed
    }

    /**
     * Removes multiple roots at once.
     *
     * @param uris A list of root URIs to remove.
     * @return The number of roots that were successfully removed.
     * @throws IllegalStateException If the client does not support roots.
     */
    public fun removeRoots(uris: List<String>): Int {
        if (capabilities.roots == null) {
            logger.error { "Failed to remove roots: Client does not support roots capability" }
            throw IllegalStateException("Client does not support roots capability.")
        }
        logger.info { "Removing ${uris.size} roots" }

        val oldMap = roots.getAndUpdate { current -> current - uris.toPersistentSet() }

        val removedCount = uris.count { it in oldMap }

        logger.info {
            if (removedCount > 0) {
                "Removed $removedCount roots"
            } else {
                "No roots were removed"
            }
        }
        return removedCount
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

    /**
     * Sets the elicitation handler.
     *
     * @param handler The elicitation handler.
     * @throws IllegalStateException if the client does not support elicitation.
     */
    public fun setElicitationHandler(handler: (CreateElicitationRequest) -> CreateElicitationResult) {
        if (capabilities.elicitation == null) {
            logger.error { "Failed to set elicitation handler: Client does not support elicitation" }
            throw IllegalStateException("Client does not support elicitation.")
        }
        logger.info { "Setting the elicitation handler" }

        setRequestHandler<CreateElicitationRequest>(Method.Defined.ElicitationCreate) { request, _ ->
            handler(request)
        }
    }

    // --- Internal Handlers ---

    private suspend fun handleListRoots(): ListRootsResult {
        val rootList = roots.value.values.toList()
        return ListRootsResult(rootList)
    }

    /**
     * Validates meta keys according to MCP specification.
     *
     * Key format: [prefix/]name
     * - Prefix (optional): dot-separated labels + slash
     * - Reserved prefixes contain "modelcontextprotocol" or "mcp" as complete labels
     * - Name: alphanumeric start/end, may contain hyphens, underscores, dots (empty allowed)
     */
    private fun validateMetaKeys(keys: Set<String>) {
        val labelPattern = Regex("[a-zA-Z]([a-zA-Z0-9-]*[a-zA-Z0-9])?")
        val namePattern = Regex("[a-zA-Z0-9]([a-zA-Z0-9._-]*[a-zA-Z0-9])?")

        keys.forEach { key ->
            require(key.isNotEmpty()) { "Meta key cannot be empty" }

            val (prefix, name) = key.split('/', limit = 2).let { parts ->
                when (parts.size) {
                    1 -> null to parts[0]
                    2 -> parts[0] to parts[1]
                    else -> throw IllegalArgumentException("Unexpected split result for key: $key")
                }
            }

            // Validate prefix if present
            prefix?.let {
                require(it.isNotEmpty()) { "Invalid _meta key '$key': prefix cannot be empty" }

                val labels = it.split('.')
                require(labels.all { label -> label.matches(labelPattern) }) {
                    "Invalid _meta key '$key': prefix labels must start with a letter, end with letter/digit, and contain only letters, digits, or hyphens"
                }

                require(
                    labels.none { label ->
                        label.equals("modelcontextprotocol", ignoreCase = true) ||
                            label.equals("mcp", ignoreCase = true)
                    },
                ) {
                    "Invalid _meta key '$key': prefix cannot contain reserved labels 'modelcontextprotocol' or 'mcp'"
                }
            }

            // Validate name (empty allowed)
            require(name.isEmpty() || name.matches(namePattern)) {
                "Invalid _meta key '$key': name must start and end with alphanumeric characters, and contain only alphanumerics, hyphens, underscores, or dots"
            }
        }
    }

    private fun convertToJsonMap(map: Map<String, Any?>): Map<String, JsonElement> = map.mapValues { (key, value) ->
        try {
            convertToJsonElement(value)
        } catch (e: Exception) {
            logger.warn { "Failed to convert value for key '$key': ${e.message}. Using string representation." }
            JsonPrimitive(value.toString())
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class, ExperimentalSerializationApi::class)
    private fun convertToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull

        is JsonElement -> value

        is String -> JsonPrimitive(value)

        is Number -> JsonPrimitive(value)

        is Boolean -> JsonPrimitive(value)

        is Char -> JsonPrimitive(value.toString())

        is Enum<*> -> JsonPrimitive(value.name)

        is Map<*, *> -> buildJsonObject { value.forEach { (k, v) -> put(k.toString(), convertToJsonElement(v)) } }

        is Collection<*> -> buildJsonArray { value.forEach { add(convertToJsonElement(it)) } }

        is Array<*> -> buildJsonArray { value.forEach { add(convertToJsonElement(it)) } }

        // Primitive arrays
        is IntArray -> buildJsonArray { value.forEach { add(it) } }

        is LongArray -> buildJsonArray { value.forEach { add(it) } }

        is FloatArray -> buildJsonArray { value.forEach { add(it) } }

        is DoubleArray -> buildJsonArray { value.forEach { add(it) } }

        is BooleanArray -> buildJsonArray { value.forEach { add(it) } }

        is ShortArray -> buildJsonArray { value.forEach { add(it) } }

        is ByteArray -> buildJsonArray { value.forEach { add(it) } }

        is CharArray -> buildJsonArray { value.forEach { add(it.toString()) } }

        // Unsigned arrays
        is UIntArray -> buildJsonArray { value.forEach { add(JsonPrimitive(it)) } }

        is ULongArray -> buildJsonArray { value.forEach { add(JsonPrimitive(it)) } }

        is UShortArray -> buildJsonArray { value.forEach { add(JsonPrimitive(it)) } }

        is UByteArray -> buildJsonArray { value.forEach { add(JsonPrimitive(it)) } }

        else -> {
            logger.debug { "Converting unknown type ${value::class} to string: $value" }
            JsonPrimitive(value.toString())
        }
    }
}
