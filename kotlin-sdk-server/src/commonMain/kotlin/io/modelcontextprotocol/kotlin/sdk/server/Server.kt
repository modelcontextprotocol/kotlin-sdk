package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.shared.ProtocolOptions
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListPromptsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListPromptsResult
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesResult
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesResult
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.Notification
import io.modelcontextprotocol.kotlin.sdk.types.Prompt
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Resource
import io.modelcontextprotocol.kotlin.sdk.types.ResourceUpdatedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.UnsubscribeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.serialization.json.JsonObject
import kotlin.time.ExperimentalTime

private val logger = KotlinLogging.logger {}

/**
 * Configuration options for the MCP server.
 *
 * @property capabilities The capabilities this server supports.
 * @property enforceStrictCapabilities Whether to strictly enforce capabilities when interacting with clients.
 */
public class ServerOptions(public val capabilities: ServerCapabilities, enforceStrictCapabilities: Boolean = true) :
    ProtocolOptions(enforceStrictCapabilities = enforceStrictCapabilities)

/**
 * An MCP server is responsible for storing features and handling new connections.
 *
 * This server automatically responds to the initialization flow as initiated by the client.
 * You can register tools, prompts, and resources using [addTool], [addPrompt], and [addResource].
 * The server will then automatically handle listing and retrieval requests from the client.
 *
 * In case the server supports feature list notification or resource substitution,
 * the server will automatically send notifications for all connected clients.
 * Currently, after subscription to a resource, the server will NOT send the subscription confirmation
 * as this response schema is not defined in the protocol.
 *
 * @param serverInfo Information about this server implementation (name, version).
 * @param options Configuration options for the server.
 * @param instructionsProvider Optional provider for instructions from the server to the client about how to use
 * this server. The provider is called each time a new session is started to support dynamic instructions.
 * @param block A block to configure the mcp server.
 */
public open class Server(
    protected val serverInfo: Implementation,
    protected val options: ServerOptions,
    protected val instructionsProvider: (() -> String)? = null,
    block: Server.() -> Unit = {},
) {
    /**
     * Alternative constructor that provides the instructions directly as a string.
     *
     * @param serverInfo Information about this server implementation (name, version).
     * @param options Configuration options for the server.
     * @param instructions Instructions from the server to the client about how to use this server.
     * @param block A block to configure the mcp server.
     */
    public constructor(
        serverInfo: Implementation,
        options: ServerOptions,
        instructions: String,
        block: Server.() -> Unit = {},
    ) : this(serverInfo, options, { instructions }, block)

    private var _onInitialized: (() -> Unit) = {}

    private var _onConnect: (() -> Unit) = {}

    private var _onClose: () -> Unit = {}

    @OptIn(ExperimentalTime::class)
    private val notificationService = FeatureNotificationService()

    private val sessionRegistry = ServerSessionRegistry()

    private val toolRegistry = FeatureRegistry<RegisteredTool>("Tool").apply {
        if (options.capabilities.tools?.listChanged ?: false) {
            addListener(notificationService.toolListChangedListener)
        }
    }
    private val promptRegistry = FeatureRegistry<RegisteredPrompt>("Prompt").apply {
        if (options.capabilities.prompts?.listChanged ?: false) {
            addListener(notificationService.promptListChangedListener)
        }
    }
    private val resourceRegistry = FeatureRegistry<RegisteredResource>("Resource").apply {
        if (options.capabilities.resources?.listChanged ?: false) {
            addListener(notificationService.resourceListChangedListener)
        }
        if (options.capabilities.resources?.subscribe ?: false) {
            addListener(notificationService.resourceUpdatedListener)
        }
    }

    /**
     * Provides a snapshot of all sessions currently registered in the server
     */
    public val sessions: Map<ServerSessionKey, ServerSession>
        get() = sessionRegistry.sessions

    /**
     * Provides a snapshot of all tools currently registered in the server
     */
    public val tools: Map<String, RegisteredTool>
        get() = toolRegistry.values

    /**
     * Provides a snapshot of all prompts currently registered in the server
     */
    public val prompts: Map<String, RegisteredPrompt>
        get() = promptRegistry.values

    /**
     * Provides a snapshot of all resources currently registered in the server
     */
    public val resources: Map<String, RegisteredResource>
        get() = resourceRegistry.values

    init {
        block(this)
    }

    public suspend fun close() {
        logger.debug { "Closing MCP server" }
        notificationService.close()
        sessions.forEach { (sessionId, session) ->
            logger.info { "Closing session $sessionId" }
            session.close()
        }
        _onClose()
    }

    /**
     * Starts a new server session with the given transport and initializes
     * internal request handlers based on the server's capabilities.
     *
     * @param transport The transport layer to connect the session with.
     * @return The initialized and connected server session.
     */
    @Deprecated(
        "Use createSession(transport) instead.",
        ReplaceWith("createSession(transport)"),
        DeprecationLevel.ERROR,
    )
    public suspend fun connect(transport: Transport): ServerSession = createSession(transport)

    /**
     * Starts a new server session with the given transport and initializes
     * internal request handlers based on the server's capabilities.
     *
     * @param transport The transport layer to connect the session with.
     * @return The initialized and connected server session.
     */
    public suspend fun createSession(transport: Transport): ServerSession {
        val session = ServerSession(serverInfo, options, instructionsProvider?.invoke())

        // Internal handlers for tools
        if (options.capabilities.tools != null) {
            session.setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { _, _ ->
                handleListTools()
            }
            session.setRequestHandler<CallToolRequest>(Method.Defined.ToolsCall) { request, _ ->
                handleCallTool(session, request)
            }
        }

        // Internal handlers for prompts
        if (options.capabilities.prompts != null) {
            session.setRequestHandler<ListPromptsRequest>(Method.Defined.PromptsList) { _, _ ->
                handleListPrompts()
            }
            session.setRequestHandler<GetPromptRequest>(Method.Defined.PromptsGet) { request, _ ->
                handleGetPrompt(session, request)
            }
        }

        // Internal handlers for resources
        if (options.capabilities.resources != null) {
            session.setRequestHandler<ListResourcesRequest>(Method.Defined.ResourcesList) { _, _ ->
                handleListResources()
            }
            session.setRequestHandler<ReadResourceRequest>(Method.Defined.ResourcesRead) { request, _ ->
                handleReadResource(session, request)
            }
            session.setRequestHandler<ListResourceTemplatesRequest>(Method.Defined.ResourcesTemplatesList) { _, _ ->
                handleListResourceTemplates()
            }
            if (options.capabilities.resources?.subscribe ?: false) {
                session.setRequestHandler<SubscribeRequest>(Method.Defined.ResourcesSubscribe) { request, _ ->
                    handleSubscribeResources(session, request)
                    // Does not return any confirmation as the structure is not stated in the protocol
                    null
                }
                session.setRequestHandler<UnsubscribeRequest>(Method.Defined.ResourcesUnsubscribe) { request, _ ->
                    handleUnsubscribeResources(session, request)
                    // Does not return any confirmation as the structure is not stated in the protocol
                    null
                }
            }
        }

        // Register cleanup handler to remove session from list when it closes
        session.onClose {
            logger.debug { "Removing closed session from active sessions list" }
            notificationService.unsubscribeSession(session)
            sessionRegistry.removeSession(session.sessionId)
        }

        logger.debug { "Server session connecting to transport" }
        session.connect(transport)
        logger.debug { "Server session successfully connected to transport" }
        sessionRegistry.addSession(session)
        notificationService.subscribeSession(session)

        _onConnect()
        return session
    }

    /**
     * Registers a callback to be invoked when the new server session connected.
     */
    public fun onConnect(block: () -> Unit) {
        val old = _onConnect
        _onConnect = {
            old()
            block()
        }
    }

    /**
     * Registers a callback to be invoked when the server has completed initialization.
     */
    @Deprecated(
        "Initialization moved to ServerSession, use ServerSession.onInitialized instead.",
        ReplaceWith("ServerSession.onInitialized"),
        DeprecationLevel.ERROR,
    )
    public fun onInitialized(block: () -> Unit) {
        val old = _onInitialized
        _onInitialized = {
            old()
            block()
        }
    }

    /**
     * Registers a callback to be invoked when the server connection is closing.
     */
    public fun onClose(block: () -> Unit) {
        val old = _onClose
        _onClose = {
            old()
            block()
        }
    }

    /**
     * Registers a single tool. The client can then call this tool.
     *
     * @param tool A [Tool] object describing the tool.
     * @param handler A suspend function that handles executing the tool when called by the client.
     * @throws IllegalStateException If the server does not support tools.
     */
    public fun addTool(tool: Tool, handler: suspend Context.(CallToolRequest) -> CallToolResult) {
        check(options.capabilities.tools != null) {
            logger.error { "Failed to add tool '${tool.name}': Server does not support tools capability" }
            "Server does not support tools capability. Enable it in ServerOptions."
        }

        toolRegistry.add(RegisteredTool(tool, handler))
    }

    /**
     * Registers a single tool. The client can then call this tool.
     *
     * @param name The name of the tool.
     * @param title An optional human-readable name of the tool for display purposes.
     * @param description A human-readable description of what the tool does.
     * @param inputSchema The expected input schema for the tool.
     * @param outputSchema The optional expected output schema for the tool.
     * @param toolAnnotations Optional additional tool information.
     * @param meta Optional metadata as a [JsonObject].
     * @param handler A suspend function that handles executing the tool when called by the client.
     * @throws IllegalStateException If the server does not support tools.
     */
    public fun addTool(
        name: String,
        description: String,
        inputSchema: ToolSchema = ToolSchema(),
        title: String? = null,
        outputSchema: ToolSchema? = null,
        toolAnnotations: ToolAnnotations? = null,
        meta: JsonObject? = null,
        handler: suspend Context.(CallToolRequest) -> CallToolResult,
    ) {
        val tool = Tool(
            name = name,
            inputSchema = inputSchema,
            outputSchema = outputSchema,
            description = description,
            title = title,
            annotations = toolAnnotations,
            meta = meta,
        )
        addTool(tool, handler)
    }

    /**
     * Registers multiple tools at once.
     *
     * @param toolsToAdd A list of [RegisteredTool] objects representing the tools to register.
     * @throws IllegalStateException If the server does not support tools.
     */
    public fun addTools(toolsToAdd: List<RegisteredTool>) {
        check(options.capabilities.tools != null) {
            logger.error { "Failed to add tools: Server does not support tools capability" }
            "Server does not support tools capability."
        }
        toolRegistry.addAll(toolsToAdd)
    }

    /**
     * Removes a single tool by name.
     *
     * @param name The name of the tool to remove.
     * @return True if the tool was removed, false if it wasn't found.
     * @throws IllegalStateException If the server does not support tools.
     */
    public fun removeTool(name: String): Boolean {
        check(options.capabilities.tools != null) {
            logger.error { "Failed to remove tool '$name': Server does not support tools capability" }
            "Server does not support tools capability."
        }
        return toolRegistry.remove(name)
    }

    /**
     * Removes multiple tools at once.
     *
     * @param toolNames A list of tool names to remove.
     * @return The number of tools that were successfully removed.
     * @throws IllegalStateException If the server does not support tools.
     */
    public fun removeTools(toolNames: List<String>): Int {
        check(options.capabilities.tools != null) {
            logger.error { "Failed to remove tools: Server does not support tools capability" }
            "Server does not support tools capability."
        }

        val removedCount = toolRegistry.removeAll(toolNames)
        return removedCount
    }

    /**
     * Registers a single prompt. The client can then retrieve the prompt.
     *
     * @param prompt A [Prompt] object describing the prompt.
     * @param promptProvider A suspend function that returns the prompt content when requested by the client.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public fun addPrompt(prompt: Prompt, promptProvider: suspend Context.(GetPromptRequest) -> GetPromptResult) {
        check(options.capabilities.prompts != null) {
            logger.error { "Failed to add prompt '${prompt.name}': Server does not support prompts capability" }
            "Server does not support prompts capability."
        }
        promptRegistry.add(RegisteredPrompt(prompt, promptProvider))
    }

    /**
     * Registers a single prompt by constructing a [Prompt] from given parameters.
     *
     * @param name The name of the prompt.
     * @param description An optional human-readable description of the prompt.
     * @param arguments An optional list of [PromptArgument] that the prompt accepts.
     * @param promptProvider A suspend function that returns the prompt content when requested.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public fun addPrompt(
        name: String,
        description: String? = null,
        arguments: List<PromptArgument>? = null,
        promptProvider: suspend Context.(GetPromptRequest) -> GetPromptResult,
    ) {
        val prompt = Prompt(name = name, description = description, arguments = arguments)
        addPrompt(prompt, promptProvider)
    }

    /**
     * Registers multiple prompts at once.
     *
     * @param promptsToAdd A list of [RegisteredPrompt] objects representing the prompts to register.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public fun addPrompts(promptsToAdd: List<RegisteredPrompt>) {
        check(options.capabilities.prompts != null) {
            logger.error { "Failed to add prompts: Server does not support prompts capability" }
            "Server does not support prompts capability."
        }
        promptRegistry.addAll(promptsToAdd)
    }

    /**
     * Removes a single prompt by name.
     *
     * @param name The name of the prompt to remove.
     * @return True if the prompt was removed, false if it wasn't found.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public fun removePrompt(name: String): Boolean {
        check(options.capabilities.prompts != null) {
            logger.error { "Failed to remove prompt '$name': Server does not support prompts capability" }
            "Server does not support prompts capability."
        }

        return promptRegistry.remove(name)
    }

    /**
     * Removes multiple prompts at once.
     *
     * @param promptNames A list of prompt names to remove.
     * @return The number of prompts that were successfully removed.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public fun removePrompts(promptNames: List<String>): Int {
        check(options.capabilities.prompts != null) {
            logger.error { "Failed to remove prompts: Server does not support prompts capability" }
            "Server does not support prompts capability."
        }

        return promptRegistry.removeAll(promptNames)
    }

    /**
     * Registers a single resource. The client can then read the resource content.
     *
     * @param uri The URI of the resource.
     * @param name A human-readable name for the resource.
     * @param description A description of the resource's content.
     * @param mimeType The MIME type of the resource content.
     * @param readHandler A suspend function that returns the resource content when read by the client.
     * @throws IllegalStateException If the server does not support resources.
     */
    public fun addResource(
        uri: String,
        name: String,
        description: String,
        mimeType: String = "text/html",
        readHandler: suspend Context.(ReadResourceRequest) -> ReadResourceResult,
    ) {
        check(options.capabilities.resources != null) {
            logger.error { "Failed to add resource '$name': Server does not support resources capability" }
            "Server does not support resources capability."
        }
        val resource = Resource(uri, name, description, mimeType)
        resourceRegistry.add(RegisteredResource(resource, readHandler))
    }

    /**
     * Registers multiple resources at once.
     *
     * @param resourcesToAdd A list of [RegisteredResource] objects representing the resources to register.
     * @throws IllegalStateException If the server does not support resources.
     */
    public fun addResources(resourcesToAdd: List<RegisteredResource>) {
        check(options.capabilities.resources != null) {
            logger.error { "Failed to add resources: Server does not support resources capability" }
            "Server does not support resources capability."
        }
        resourceRegistry.addAll(resourcesToAdd)
    }

    /**
     * Removes a single resource by URI.
     *
     * @param uri The URI of the resource to remove.
     * @return True if the resource was removed, false if it wasn't found.
     * @throws IllegalStateException If the server does not support resources.
     */
    public fun removeResource(uri: String): Boolean {
        check(options.capabilities.resources != null) {
            logger.error { "Failed to remove resource '$uri': Server does not support resources capability" }
            "Server does not support resources capability."
        }
        return resourceRegistry.remove(uri)
    }

    /**
     * Removes multiple resources at once.
     *
     * @param uris A list of resource URIs to remove.
     * @return The number of resources that were successfully removed.
     * @throws IllegalStateException If the server does not support resources.
     */
    public fun removeResources(uris: List<String>): Int {
        check(options.capabilities.resources != null) {
            logger.error { "Failed to remove resources: Server does not support resources capability" }
            "Server does not support resources capability."
        }
        return resourceRegistry.removeAll(uris)
    }

    // --- Internal Handlers ---
    private fun handleSubscribeResources(session: ServerSession, request: SubscribeRequest) {
        if (options.capabilities.resources?.subscribe ?: false) {
            logger.debug { "Subscribing to resources" }
            notificationService.subscribeToResourceUpdate(session, request.params.uri)
        } else {
            logger.debug { "Failed to subscribe to resources: Server does not support resources capability" }
        }
    }

    private fun handleUnsubscribeResources(session: ServerSession, request: UnsubscribeRequest) {
        if (options.capabilities.resources?.subscribe ?: false) {
            logger.debug { "Unsubscribing from resources" }
            notificationService.unsubscribeFromResourceUpdate(session, request.params.uri)
        } else {
            logger.debug { "Failed to unsubscribe from resources: Server does not support resources capability" }
        }
    }

    private fun handleListTools(): ListToolsResult {
        val toolList = tools.values.map { it.tool }
        return ListToolsResult(tools = toolList, nextCursor = null)
    }

    private suspend fun handleCallTool(session: ServerSession, request: CallToolRequest): CallToolResult {
        val requestParams = request.params
        logger.debug { "Handling tool call request for tool: ${requestParams.name}" }

        // Check if the tool exists
        val tool = toolRegistry.get(requestParams.name) ?: run {
            logger.error { "Tool not found: ${requestParams.name}" }
            return CallToolResult(
                content = listOf(TextContent(text = "Tool ${requestParams.name} not found")),
                isError = true,
            )
        }

        // Execute the tool handler and catch any errors
        return try {
            logger.trace { "Executing tool ${requestParams.name} with input: ${requestParams.arguments}" }
            tool.run {
                ContextImpl(session).handler(request)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error executing tool ${requestParams.name}" }
            CallToolResult(
                content = listOf(TextContent(text = "Error executing tool ${requestParams.name}: ${e.message}")),
                isError = true,
            )
        }
    }

    private fun handleListPrompts(): ListPromptsResult {
        logger.debug { "Handling list prompts request" }
        return ListPromptsResult(prompts = prompts.values.map { it.prompt })
    }

    private suspend fun handleGetPrompt(session: ServerSession, request: GetPromptRequest): GetPromptResult {
        val requestParams = request.params
        logger.debug { "Handling get prompt request for: ${requestParams.name}" }
        val prompt = promptRegistry.get(requestParams.name)
            ?: run {
                logger.error { "Prompt not found: ${requestParams.name}" }
                throw IllegalArgumentException("Prompt not found: ${requestParams.name}")
            }
        return prompt.run {
            ContextImpl(session).messageProvider(request)
        }
    }

    private fun handleListResources(): ListResourcesResult {
        logger.debug { "Handling list resources request" }
        return ListResourcesResult(resources = resources.values.map { it.resource })
    }

    private suspend fun handleReadResource(session: ServerSession, request: ReadResourceRequest): ReadResourceResult {
        val requestParams = request.params
        logger.debug { "Handling read resource request for: ${requestParams.uri}" }
        val resource = resourceRegistry.get(requestParams.uri)
            ?: run {
                logger.error { "Resource not found: ${requestParams.uri}" }
                throw IllegalArgumentException("Resource not found: ${requestParams.uri}")
            }
        return resource.run {
            ContextImpl(session).readHandler(request)
        }
    }

    private fun handleListResourceTemplates(): ListResourceTemplatesResult {
        // If you have resource templates, return them here. For now, return empty.
        return ListResourceTemplatesResult(listOf())
    }

    // Start the ServerSession redirection section

    /**
     * Triggers [ServerSession.ping] request for session by provided [sessionId].
     * @param sessionId The session ID to ping
     */
    public suspend fun ping(sessionId: String): EmptyResult = with(sessionRegistry.getSession(sessionId)) {
        ping()
    }

    /**
     * Triggers [ServerSession.createMessage] request for session by provided [sessionId].
     *
     * @param sessionId The session ID to create a message.
     * @param params The parameters for creating a message.
     * @param options Optional request options.
     * @return The created message result.
     * @throws IllegalStateException If the server does not support sampling or if the request fails.
     */
    public suspend fun createMessage(
        sessionId: String,
        params: CreateMessageRequest,
        options: RequestOptions? = null,
    ): CreateMessageResult = with(sessionRegistry.getSession(sessionId)) {
        request(params, options)
    }

    /**
     * Triggers [ServerSession.listRoots] request for session by provided [sessionId].
     *
     * @param sessionId The session ID to list roots for.
     * @param params JSON parameters for the request, usually empty.
     * @param options Optional request options.
     * @return The list of roots.
     * @throws IllegalStateException If the server or client does not support roots.
     */
    public suspend fun listRoots(
        sessionId: String,
        params: JsonObject = EmptyJsonObject,
        options: RequestOptions? = null,
    ): ListRootsResult = with(sessionRegistry.getSession(sessionId)) {
        listRoots(params, options)
    }

    /**
     * Triggers [ServerSession.createElicitation] request for session by provided [sessionId].
     *
     * @param sessionId The session ID to create elicitation for.
     * @param message The elicitation message.
     * @param requestedSchema The requested schema for the elicitation.
     * @param options Optional request options.
     * @return The created elicitation result.
     * @throws IllegalStateException If the server does not support elicitation or if the request fails.
     */
    public suspend fun createElicitation(
        sessionId: String,
        message: String,
        requestedSchema: ElicitRequestParams.RequestedSchema,
        options: RequestOptions? = null,
    ): ElicitResult = with(sessionRegistry.getSession(sessionId)) {
        createElicitation(message, requestedSchema, options)
    }

    /**
     * Triggers [ServerSession.sendLoggingMessage] for session by provided [sessionId].
     *
     * @param sessionId The session ID to send the logging message to.
     * @param notification The logging message notification.
     */
    public suspend fun sendLoggingMessage(sessionId: String, notification: LoggingMessageNotification) {
        with(sessionRegistry.getSession(sessionId)) {
            sendLoggingMessage(notification)
        }
    }

    /**
     * Triggers [ServerSession.sendResourceUpdated] for session by provided [sessionId].
     *
     * @param sessionId The session ID to send the resource updated notification to.
     * @param notification Details of the updated resource.
     */
    public suspend fun sendResourceUpdated(sessionId: String, notification: ResourceUpdatedNotification) {
        with(sessionRegistry.getSession(sessionId)) {
            sendResourceUpdated(notification)
        }
    }

    /**
     * Triggers [ServerSession.sendResourceListChanged] for session by provided [sessionId].
     *
     * @param sessionId The session ID to send the resource list changed notification to.
     */
    public suspend fun sendResourceListChanged(sessionId: String) {
        with(sessionRegistry.getSession(sessionId)) {
            sendResourceListChanged()
        }
    }

    /**
     * Triggers [ServerSession.sendToolListChanged] for session by provided [sessionId].
     *
     * @param sessionId The session ID to send the tool list changed notification to.
     */
    public suspend fun sendToolListChanged(sessionId: String) {
        with(sessionRegistry.getSession(sessionId)) {
            sendToolListChanged()
        }
    }

    /**
     * Triggers [ServerSession.sendPromptListChanged] for session by provided [sessionId].
     *
     * @param sessionId The session ID to send the prompt list changed notification to.
     */
    public suspend fun sendPromptListChanged(sessionId: String) {
        with(sessionRegistry.getSession(sessionId)) {
            sendPromptListChanged()
        }
    }
    // End the ServerSession redirection section

    // Start the notification handling section
    public fun <T : Notification> setNotificationHandler(method: Method, handler: (notification: T) -> Deferred<Unit>) {
        sessions.forEach { (_, session) ->
            session.setNotificationHandler(method, handler)
        }
    }

    public fun removeNotificationHandler(method: Method) {
        sessions.forEach { (_, session) ->
            session.removeNotificationHandler(method)
        }
    }

    public fun <T : Notification> setNotificationHandler(
        sessionId: String,
        method: Method,
        handler: (notification: T) -> Deferred<Unit>,
    ) {
        sessionRegistry.getSessionOrNull(sessionId)?.setNotificationHandler(method, handler)
    }

    public fun removeNotificationHandler(sessionId: String, method: Method) {
        sessionRegistry.getSessionOrNull(sessionId)?.removeNotificationHandler(method)
    }
    // End the notification handling section
}
