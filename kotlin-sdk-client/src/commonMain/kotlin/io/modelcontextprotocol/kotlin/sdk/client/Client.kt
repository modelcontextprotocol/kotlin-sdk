package io.modelcontextprotocol.kotlin.sdk.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import io.modelcontextprotocol.kotlin.sdk.shared.Protocol
import io.modelcontextprotocol.kotlin.sdk.shared.ProtocolOptions
import io.modelcontextprotocol.kotlin.sdk.shared.RequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.BooleanSchema
import io.modelcontextprotocol.kotlin.sdk.types.CacheScope
import io.modelcontextprotocol.kotlin.sdk.types.CacheableResult
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CompleteRequest
import io.modelcontextprotocol.kotlin.sdk.types.CompleteResult
import io.modelcontextprotocol.kotlin.sdk.types.CreateMessageRequest
import io.modelcontextprotocol.kotlin.sdk.types.DiscoverRequest
import io.modelcontextprotocol.kotlin.sdk.types.DiscoverRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.DiscoverResult
import io.modelcontextprotocol.kotlin.sdk.types.DoubleSchema
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestFormParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitationCompleteNotification
import io.modelcontextprotocol.kotlin.sdk.types.EmptyResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.HANDSHAKE_PROTOCOL_VERSIONS
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.IntegerSchema
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_HANDSHAKE_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_MODERN_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.LegacyTitledEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.ListPromptsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListPromptsResult
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourceTemplatesResult
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesResult
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListRootsResult
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.MODERN_PROTOCOL_VERSIONS
import io.modelcontextprotocol.kotlin.sdk.types.McpException
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.PingRequest
import io.modelcontextprotocol.kotlin.sdk.types.PrimitiveSchemaDefinition
import io.modelcontextprotocol.kotlin.sdk.types.RPCError
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Request
import io.modelcontextprotocol.kotlin.sdk.types.RequestEnvelope
import io.modelcontextprotocol.kotlin.sdk.types.RequestMeta
import io.modelcontextprotocol.kotlin.sdk.types.RequestResult
import io.modelcontextprotocol.kotlin.sdk.types.Root
import io.modelcontextprotocol.kotlin.sdk.types.RootsListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.SetLevelRequest
import io.modelcontextprotocol.kotlin.sdk.types.SetLevelRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.StringSchema
import io.modelcontextprotocol.kotlin.sdk.types.SubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.TitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.TitledSingleSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UnsubscribeRequest
import io.modelcontextprotocol.kotlin.sdk.types.UntitledMultiSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.UntitledSingleSelectEnumSchema
import io.modelcontextprotocol.kotlin.sdk.types.isModernProtocolVersion
import io.modelcontextprotocol.kotlin.sdk.types.serverInfo
import io.modelcontextprotocol.kotlin.sdk.types.supportsUrl
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import io.modelcontextprotocol.kotlin.sdk.types.toMeta
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val logger = KotlinLogging.logger {}

/**
 * Options for configuring the MCP client.
 *
 * @property capabilities The capabilities this client supports.
 * @param enforceStrictCapabilities Whether to strictly enforce capabilities when interacting with the server.
 * @param handlerCoroutineContext Coroutine context for inbound handlers. See [ProtocolOptions.handlerCoroutineContext].
 * @property versionNegotiation How [Client.connect] settles which protocol lifecycle to speak.
 *   Defaults to the `initialize` handshake, the only lifecycle offering sampling, elicitation,
 *   roots, `ping`, `logging/setLevel` and resource subscriptions. Set [VersionNegotiationMode.Auto]
 *   to probe `server/discover` and adopt the request-scoped lifecycle where the server offers it.
 * @property logLevel Minimum severity of `notifications/message` a server may emit while serving
 *   this client's requests, on the request-scoped lifecycle only, where a server emits nothing for
 *   a request that did not ask. Ignored on the handshake lifecycle, where [Client.setLoggingLevel]
 *   applies instead.
 * @property responseCache Where to keep results a server marked reusable. Off by default;
 *   [InMemoryResponseCacheStore] caches for the lifetime of this client. Consulted only on the
 *   request-scoped lifecycle, the only one whose results carry caching directives.
 * @property cachePartition The authorization context this client fetches under, which
 *   [CacheScope.Private] entries are confined to. Only meaningful for a store shared across
 *   principals; the default scopes such entries to this client instance.
 */
public class ClientOptions(
    public val capabilities: ClientCapabilities = ClientCapabilities(),
    enforceStrictCapabilities: Boolean = true,
    handlerCoroutineContext: CoroutineContext = Dispatchers.Default,
    @property:ExperimentalMcpApi
    public val versionNegotiation: VersionNegotiationMode = VersionNegotiationMode.Legacy,
    @property:ExperimentalMcpApi
    public val logLevel: LoggingLevel? = null,
    @property:ExperimentalMcpApi
    public val responseCache: ResponseCacheStore? = null,
    @property:ExperimentalMcpApi
    public val cachePartition: String = "",
) : ProtocolOptions(
    enforceStrictCapabilities = enforceStrictCapabilities,
    handlerCoroutineContext = handlerCoroutineContext,
)

/**
 * Initializes and connects an MCP client using the provided clientInfo [Implementation], client options,
 * and transport mechanism.
 *
 * @param clientInfo The implementation details of the MCP client, including its name, version, and other metadata.
 * @param clientOptions Optional client configuration settings, such as supported capabilities
 *      and strict enforcement options. Defaults to a new instance of [ClientOptions].
 * @param transport The transport mechanism used for communication.
 * @return An instance of [Client] that is connected and ready for use with the specified transport.
 */
@ExperimentalMcpApi
public suspend fun mcpClient(
    clientInfo: Implementation,
    clientOptions: ClientOptions = ClientOptions(),
    transport: Transport,
): Client {
    val client = Client(
        clientInfo = clientInfo,
        options = clientOptions,
    )
    client.connect(transport)
    return client
}

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

    @OptIn(ExperimentalMcpApi::class)
    private val versionNegotiation: VersionNegotiationMode = options.versionNegotiation

    @OptIn(ExperimentalMcpApi::class)
    private val logLevel: LoggingLevel? = options.logLevel

    @OptIn(ExperimentalMcpApi::class)
    private val responseCache: ResponseCacheStore? = options.responseCache

    @OptIn(ExperimentalMcpApi::class)
    private val cachePartition: String = options.cachePartition

    private val roots = atomic(persistentMapOf<String, Root>())

    private val _protocolVersion: AtomicRef<String?> = atomic(null)

    private val _discovered: AtomicRef<DiscoverResult?> = atomic(null)

    /**
     * The protocol version this connection settled on, or `null` before [connect] completes.
     *
     * Settled by the `initialize` handshake or by `server/discover`, depending on what the server
     * turned out to speak; [serverCapabilities], [serverVersion] and [serverInstructions] mean the
     * same thing either way.
     */
    public val protocolVersion: String? get() = _protocolVersion.value

    final override val negotiatedProtocolVersion: String? get() = _protocolVersion.value

    /**
     * Attaches this client's protocol envelope to every request sent on a request-scoped connection.
     *
     * Entries the caller put in `_meta` win over the envelope's own, and unrelated entries such as
     * `progressToken` survive untouched. On a connection settled by the handshake this returns
     * [request] unchanged.
     */
    @OptIn(ExperimentalMcpApi::class)
    final override fun decorateOutboundRequest(request: JSONRPCRequest): JSONRPCRequest {
        val version = _protocolVersion.value?.takeIf(::isModernProtocolVersion) ?: return request
        val params = (request.params as? JsonObject).orEmpty()
        val supplied = (params["_meta"] as? JsonObject).orEmpty()
        val meta = JsonObject(McpJson.encodeToJsonElement(envelope(version)).jsonObject + supplied)
        return request.copy(params = JsonObject(params + ("_meta" to meta)))
    }

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
            "tasks" -> caps?.tasks != null
            else -> true
        }

        check(hasCapability) {
            "Server does not support $capability (required for $method)"
        }
    }

    /**
     * Connects the client to the given [transport], performing the initialization handshake with the server.
     *
     * @param transport The transport to use for communication with the server.
     * @throws IllegalStateException If the server's protocol version is not supported.
     */
    @OptIn(ExperimentalMcpApi::class)
    override suspend fun connect(transport: Transport) {
        super.connect(transport)

        try {
            if (!negotiateRequestScoped(transport)) {
                handshake(transport)
            }
            enableConcurrentDispatch()
        } catch (error: Throwable) {
            logger.error(error) { "Failed to initialize client: ${error.message}" }
            close()

            when (error) {
                is CancellationException,
                is McpException,
                is StreamableHttpError,
                is SerializationException,
                -> throw error

                else -> throw IllegalStateException("Error connecting to transport: ${error.message}", error)
            }
        }
    }

    /** The plain `initialize` handshake, unchanged from a client built without version negotiation. */
    private suspend fun handshake(transport: Transport) {
        val message = InitializeRequest(
            InitializeRequestParams(
                protocolVersion = LATEST_HANDSHAKE_VERSION,
                capabilities = capabilities,
                clientInfo = clientInfo,
            ),
        )
        val result = request<InitializeResult>(message)

        if (!HANDSHAKE_PROTOCOL_VERSIONS.contains(result.protocolVersion)) {
            error(
                "Server's protocol version is not supported: ${result.protocolVersion}",
            )
        }

        serverCapabilities = result.capabilities
        serverVersion = result.serverInfo
        serverInstructions = result.instructions
        settle(result.protocolVersion, transport)

        notification(InitializedNotification())
    }

    /**
     * Probes `server/discover` and adopts the request-scoped lifecycle when the answer says to.
     *
     * @return `true` when the connection settled on that lifecycle, `false` when the caller should
     * run the handshake instead
     */
    @OptIn(ExperimentalMcpApi::class)
    private suspend fun negotiateRequestScoped(transport: Transport): Boolean {
        val mode = versionNegotiation
        if (mode == VersionNegotiationMode.Legacy) return false

        val offered = if (mode is VersionNegotiationMode.Pin) listOf(mode.version) else MODERN_PROTOCOL_VERSIONS
        val fallbackAvailable = mode !is VersionNegotiationMode.Pin
        var version = offered.first()
        var corrected = false

        while (true) {
            val outcome = probeDiscovery(version, transport)
            val verdict = classifyProbeOutcome(
                outcome = outcome,
                clientModernVersions = offered,
                fallbackAvailable = fallbackAvailable,
                overStdio = transport is StdioClientTransport,
            )
            when (verdict) {
                is ProbeVerdict.Modern -> {
                    adopt(verdict.version, verdict.discover, transport)
                    return true
                }

                is ProbeVerdict.Corrective -> {
                    // The revision mandates one corrective continuation. A server that rejects the
                    // version it just named is not negotiating, so the second rejection is reported.
                    if (corrected) throw (outcome as ProbeOutcome.Refused).error
                    corrected = true
                    version = verdict.version
                }

                ProbeVerdict.Legacy -> return false

                is ProbeVerdict.Failed -> throw verdict.cause
            }
        }
    }

    /**
     * Sends one `server/discover` probe at [version].
     *
     * The probe rides the ordinary request path, so it consumes a request id and honours the
     * configured timeout. [version] is settled for the duration of the probe — it is what admits a
     * request-scoped method, attaches the envelope and sets the `MCP-Protocol-Version` header — and
     * withdrawn afterwards, so a fallback handshake carries none of the three.
     */
    @OptIn(ExperimentalMcpApi::class)
    private suspend fun probeDiscovery(version: String, transport: Transport): ProbeOutcome {
        settle(version, transport)
        return try {
            ProbeOutcome.Answered(
                request<DiscoverResult>(DiscoverRequest(DiscoverRequestParams(meta = envelope(version).toMeta()))),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: McpException) {
            // Silence and a peer that went away instead of answering are the same signal; on stdio
            // both mean a server that predates discovery, and the classifier decides what to do.
            if (e.code == RPCError.ErrorCode.REQUEST_TIMEOUT || e.code == RPCError.ErrorCode.CONNECTION_CLOSED) {
                ProbeOutcome.Silent
            } else {
                ProbeOutcome.Refused(e)
            }
        } catch (_: SerializationException) {
            ProbeOutcome.Answered(null)
        } catch (_: ClassCastException) {
            // The peer answered something that parsed as a different result type.
            ProbeOutcome.Answered(null)
        } finally {
            withdraw(transport)
        }
    }

    /** Undoes [settle], leaving the connection unsettled again. */
    private fun withdraw(transport: Transport) {
        _protocolVersion.value = null
        if (transport is StreamableHttpClientTransport) {
            transport.protocolVersion = null
        }
    }

    /** Adopts a discovery result as this connection's settled state. */
    @OptIn(ExperimentalMcpApi::class)
    private fun adopt(version: String, discover: DiscoverResult, transport: Transport) {
        serverCapabilities = discover.capabilities
        serverVersion = runCatching<Implementation?> { discover.meta?.serverInfo }.getOrNull()
        serverInstructions = discover.instructions
        _discovered.value = discover
        settle(version, transport)
    }

    /**
     * Records [version] as settled and pushes it onto [transport], which needs it for the
     * `MCP-Protocol-Version` header both lifecycles require.
     */
    private fun settle(version: String, transport: Transport) {
        _protocolVersion.value = version
        if (transport is StreamableHttpClientTransport) {
            transport.protocolVersion = version
        }
    }

    /**
     * The protocol versions, capabilities and instructions this server advertises.
     *
     * Answered from the result [connect] adopted when it settled the connection by discovery, so
     * asking again costs no round trip.
     *
     * @throws McpException with [RPCError.ErrorCode.METHOD_NOT_FOUND] on a connection settled by the
     * `initialize` handshake, which has no `server/discover`
     */
    @ExperimentalMcpApi
    public suspend fun discover(): DiscoverResult {
        _discovered.value?.let { return it }
        val version = _protocolVersion.value ?: LATEST_MODERN_VERSION
        return request<DiscoverResult>(DiscoverRequest(DiscoverRequestParams(meta = envelope(version).toMeta())))
            .also { _discovered.value = it }
    }

    /**
     * Serves [method] from the response cache when it holds a fresh entry, and files what [fetch]
     * answers when it does not.
     *
     * Only the request-scoped lifecycle caches, being the only one whose results carry caching
     * directives. A result that is not [CacheableResult] is passed through untouched.
     */
    @OptIn(ExperimentalMcpApi::class, ExperimentalTime::class)
    private suspend fun <T : RequestResult> cached(
        method: String,
        paramsKey: String,
        mode: CacheMode,
        fetch: suspend () -> T,
    ): T {
        val store = responseCache
        if (store == null ||
            mode == CacheMode.Bypass ||
            _protocolVersion.value?.let(::isModernProtocolVersion) != true
        ) {
            return fetch()
        }
        val key = CacheKey(method = method, paramsKey = paramsKey, partition = cachePartition)
        if (mode == CacheMode.Use) {
            store.get(key)?.takeIf { Clock.System.now() < it.expiresAt }?.let {
                @Suppress("UNCHECKED_CAST")
                return it.result as T
            }
        }
        val fresh = fetch()
        if (fresh is CacheableResult) {
            store.put(key, CacheEntry(result = fresh, expiresAt = fresh.expiryFrom(), cacheScope = fresh.cacheScope))
        }
        return fresh
    }

    /**
     * Drops every cached result this notification stales.
     *
     * Runs at the router rather than in a handler, so a stale listing is dropped whether or not the
     * application registered a handler for the notification.
     */
    @OptIn(ExperimentalMcpApi::class)
    final override suspend fun onNotificationRouted(notification: JSONRPCNotification) {
        val store = responseCache ?: return
        CACHE_INVALIDATING_NOTIFICATIONS[notification.method]?.forEach { store.invalidate(it) }
    }

    /** This client's protocol envelope for a request made under [version]. */
    @OptIn(ExperimentalMcpApi::class)
    private fun envelope(version: String): RequestEnvelope = RequestEnvelope(
        protocolVersion = version,
        clientCapabilities = capabilities,
        clientInfo = clientInfo,
        logLevel = logLevel,
    )

    override fun assertCapabilityForMethod(method: Method) {
        when (method) {
            Method.Defined.LoggingSetLevel -> {
                checkNotNull(serverCapabilities?.logging) {
                    "Server does not support logging (required for $method)"
                }
            }

            Method.Defined.PromptsGet,
            Method.Defined.PromptsList,
            -> {
                checkNotNull(serverCapabilities?.prompts) {
                    "Server does not support prompts (required for $method)"
                }
            }

            Method.Defined.CompletionComplete -> {
                checkNotNull(serverCapabilities?.completions) {
                    "Server does not support completions (required for $method)"
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

                if (method == Method.Defined.ResourcesSubscribe) {
                    check(resCaps.subscribe == true) {
                        "Server does not support resource subscriptions (required for $method)"
                    }
                }
            }

            Method.Defined.ToolsCall, Method.Defined.ToolsList -> {
                checkNotNull(serverCapabilities?.tools) {
                    "Server does not support tools (required for $method)"
                }
            }

            Method.Defined.TasksGet,
            Method.Defined.TasksResult,
            Method.Defined.TasksList,
            Method.Defined.TasksCancel,
            -> assertTasksCapabilityForMethod(method)

            Method.Defined.Initialize, Method.Defined.Ping -> {
                // No specific capability required
            }

            else -> {
                // For unknown or future methods, no assertion by default
            }
        }
    }

    private fun assertTasksCapabilityForMethod(method: Method) {
        val tasks = serverCapabilities?.tasks
            ?: error("Server does not support tasks (required for $method)")
        when (method) {
            Method.Defined.TasksList -> checkNotNull(tasks.list) {
                "Server does not support listing tasks (required for $method)"
            }

            Method.Defined.TasksCancel -> checkNotNull(tasks.cancel) {
                "Server does not support cancelling tasks (required for $method)"
            }

            else -> {
                // TasksGet, TasksResult: base tasks capability suffices.
            }
        }
    }

    override fun assertNotificationCapability(method: Method) {
        when (method) {
            Method.Defined.NotificationsRootsListChanged -> {
                check(capabilities.roots?.listChanged == true) {
                    "Client does not support roots list changed notifications (required for $method)"
                }
            }

            Method.Defined.NotificationsTasksStatus -> {
                checkNotNull(capabilities.tasks) {
                    "Client does not support tasks (required for $method)"
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
                checkNotNull(capabilities.sampling) {
                    "Client does not support sampling capability (required for $method)"
                }
            }

            Method.Defined.RootsList -> {
                checkNotNull(capabilities.roots) {
                    "Client does not support roots capability (required for $method)"
                }
            }

            Method.Defined.ElicitationCreate -> {
                checkNotNull(capabilities.elicitation) {
                    "Client does not support elicitation capability (required for $method)"
                }
            }

            Method.Defined.Ping -> {
                // No capability required
            }

            else -> {}
        }
    }

    /**
     * Wraps incoming-request handlers with SEP-1577 client-side enforcement.
     *
     * For `sampling/createMessage`: if the incoming request carries `tools` or
     * `toolChoice` but this client did not advertise [ClientCapabilities.Sampling.tools],
     * the wrapper throws an [McpException] with JSON-RPC error code `InvalidParams`
     * before the user-supplied handler runs. Matches the TypeScript SDK wrapper in
     * `Client.setRequestHandler`.
     */
    override fun <T : Request> wrapRequestHandler(
        method: Method,
        block: suspend (T, RequestHandlerExtra) -> RequestResult?,
    ): suspend (T, RequestHandlerExtra) -> RequestResult? {
        if (method != Method.Defined.SamplingCreateMessage) return block
        return { request, extra ->
            (request as? CreateMessageRequest)?.let { validateSamplingToolsCapability(it, capabilities) }
            block(request, extra)
        }
    }

    /**
     * Sends a ping request to the server to check connectivity.
     *
     * @param options Optional request options.
     * @throws IllegalStateException If the server does not support the ping method (unlikely).
     */
    public suspend fun ping(options: RequestOptions? = null): EmptyResult = request(PingRequest(), options)

    /**
     * Sends a completion request to the server, typically to generate or complete some content.
     *
     * @param params The completion request parameters.
     * @param options Optional request options.
     * @return The completion result returned by the server, or `null` if none.
     * @throws IllegalStateException If the server does not support completions.
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
    public suspend fun setLoggingLevel(level: LoggingLevel, options: RequestOptions? = null): EmptyResult =
        request(SetLevelRequest(SetLevelRequestParams(level)), options)

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
        cacheMode: CacheMode = CacheMode.Use,
    ): ListPromptsResult = cached(request.method.value, "", cacheMode) { request(request, options) }

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
        cacheMode: CacheMode = CacheMode.Use,
    ): ListResourcesResult = cached(request.method.value, "", cacheMode) { request(request, options) }

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
        cacheMode: CacheMode = CacheMode.Use,
    ): ListResourceTemplatesResult = cached(request.method.value, "", cacheMode) { request(request, options) }

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
        cacheMode: CacheMode = CacheMode.Use,
    ): ReadResourceResult = cached(request.method.value, request.params.uri, cacheMode) { request(request, options) }

    /**
     * Subscribes to resource changes on the server.
     *
     * @param request The subscription request containing resource details.
     * @param options Optional request options.
     * @throws IllegalStateException If the server does not support resource subscriptions.
     */
    public suspend fun subscribeResource(request: SubscribeRequest, options: RequestOptions? = null): EmptyResult =
        request(request, options)

    /**
     * Unsubscribes from resource changes on the server.
     *
     * @param request The unsubscribe request containing resource details.
     * @param options Optional request options.
     * @throws IllegalStateException If the server does not support resource subscriptions.
     */
    public suspend fun unsubscribeResource(request: UnsubscribeRequest, options: RequestOptions? = null): EmptyResult =
        request(request, options)

    /**
     * Calls a tool on the server by name, passing the specified arguments and metadata.
     *
     * @param name The name of the tool to call.
     * @param arguments A map of argument names to values for the tool.
     * @param meta A map of metadata key-value pairs. Keys must follow MCP specification format.
     *             - Optional prefix: dot-separated labels followed by slash (e.g., "api.example.com/")
     *             - Name: alphanumeric start/end, may contain hyphens, underscores, dots, alphanumerics
     *             - Reserved prefixes starting with "mcp" or "modelcontextprotocol" are forbidden
     * @param options Optional request options.
     * @return The result of the tool call, or `null` if none.
     * @throws IllegalStateException If the server does not support tools.
     */
    public suspend fun callTool(
        name: String,
        arguments: Map<String, Any?>,
        meta: Map<String, Any?> = emptyMap(),
        options: RequestOptions? = null,
    ): CallToolResult {
        validateMetaKeys(meta.keys)

        val jsonArguments = arguments.toJson()
        val jsonMeta = meta.toJson()

        val request = CallToolRequest(
            CallToolRequestParams(
                name = name,
                arguments = JsonObject(jsonArguments),
                meta = RequestMeta(JsonObject(jsonMeta)),
            ),
        )
        return callTool(request, options)
    }

    /**
     * Calls a tool on the server using a [CallToolRequest] object.
     *
     * @param request The request object containing the tool name and arguments.
     * @param options Optional request options.
     * @return The result of the tool call, or `null` if none.
     * @throws IllegalStateException If the server does not support tools.
     */
    public suspend fun callTool(request: CallToolRequest, options: RequestOptions? = null): CallToolResult =
        request(request, options)

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
        cacheMode: CacheMode = CacheMode.Use,
    ): ListToolsResult = cached(request.method.value, "", cacheMode) { request(request, options) }

    /**
     * Registers a single root.
     *
     * @param uri The URI of the root.
     * @param name A human-readable name for the root.
     * @throws IllegalStateException If the client does not support roots.
     */
    public fun addRoot(uri: String, name: String) {
        checkNotNull(capabilities.roots) {
            logger.error { "Failed to add root '$name': Client does not support roots capability" }
            "Client does not support roots capability."
        }
        logger.info { "Adding root: $name ($uri)" }
        roots.update { current -> current.putting(uri, Root(uri, name)) }
    }

    /**
     * Registers multiple roots at once.
     *
     * @param rootsToAdd A list of [Root] objects to register.
     * @throws IllegalStateException If the client does not support roots.
     */
    public fun addRoots(rootsToAdd: List<Root>) {
        checkNotNull(capabilities.roots) {
            logger.error { "Failed to add roots: Client does not support roots capability" }
            "Client does not support roots capability."
        }
        logger.info { "Adding ${rootsToAdd.size} roots" }
        roots.update { current -> current.puttingAll(rootsToAdd.associateBy { it.uri }) }
    }

    /**
     * Removes a single root by URI.
     *
     * @param uri The URI of the root to remove.
     * @return True if the root was removed, false if it wasn't found.
     * @throws IllegalStateException If the client does not support roots.
     */
    public fun removeRoot(uri: String): Boolean {
        checkNotNull(capabilities.roots) {
            "Client does not support roots capability."
        }
        logger.info { "Removing root: $uri" }
        val oldMap = roots.getAndUpdate { current -> current.removing(uri) }
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
        checkNotNull(capabilities.roots) {
            logger.error { "Failed to remove roots: Client does not support roots capability" }
            "Client does not support roots capability."
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
     * The handler receives both form-mode ([ElicitRequestFormParams]) and URL-mode
     * ([io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestURLParams]) requests;
     * branch on `request.params` to tell them apart. For URL mode,
     * the host application must obtain explicit user consent and display the target domain before
     * navigating — the SDK never opens or validates the URL — and should return
     * [ElicitResult.Action.Decline] or [ElicitResult.Action.Cancel] when it cannot or will not proceed.
     * A URL-mode [ElicitResult.Action.Accept] only signals consent; the outcome arrives out-of-band via
     * [setElicitationCompleteHandler].
     *
     * When a form-mode handler returns [ElicitResult.Action.Accept], any properties missing from
     * [ElicitResult.content] are automatically populated with default values defined in the
     * elicitation schema. URL-mode responses carry no content.
     *
     * @param handler The elicitation handler.
     * @throws IllegalStateException if the client does not support elicitation.
     */
    public fun setElicitationHandler(handler: (ElicitRequest) -> ElicitResult) {
        checkNotNull(capabilities.elicitation) {
            logger.error { "Failed to set elicitation handler: Client does not support elicitation" }
            "Client does not support elicitation."
        }
        logger.info { "Setting the elicitation handler" }

        setRequestHandler<ElicitRequest>(Method.Defined.ElicitationCreate) { request, _ ->
            val result = handler(request)
            applyElicitationDefaults(request, result)
        }
    }

    /**
     * Sets the handler invoked when the server reports that a URL-mode elicitation has completed.
     *
     * The handler is called for every `notifications/elicitation/complete` notification. Because the
     * server only sends this for an out-of-band (URL-mode) interaction, the client must support url-mode
     * elicitation. The client is responsible for correlating the notification's `elicitationId` with a
     * pending elicitation, ignoring unknown or already-completed identifiers, and providing a manual way
     * to continue if a notification never arrives.
     *
     * @param handler Invoked with each completion notification.
     * @throws IllegalStateException if the client does not support url-mode elicitation.
     */
    public fun setElicitationCompleteHandler(handler: (ElicitationCompleteNotification) -> Unit) {
        check(capabilities.elicitation.supportsUrl) {
            logger.error {
                "Failed to set elicitation-complete handler: client does not support url-mode elicitation"
            }
            "Client does not support url-mode elicitation."
        }
        logger.info { "Setting the elicitation-complete handler" }

        setNotificationHandler<ElicitationCompleteNotification>(
            Method.Defined.NotificationsElicitationComplete,
        ) { notification ->
            handler(notification)
            CompletableDeferred(Unit)
        }
    }

    // --- Internal Handlers ---

    private fun applyElicitationDefaults(request: ElicitRequest, result: ElicitResult): ElicitResult {
        if (result.action != ElicitResult.Action.Accept) return result
        val formParams = request.params as? ElicitRequestFormParams ?: return result
        val content = result.content ?: return result

        val merged = buildMap {
            putAll(content)
            for ((key, schemaDef) in formParams.requestedSchema.properties) {
                if (key !in content) {
                    schemaDef.defaultJsonValue()?.let { put(key, it) }
                }
            }
        }

        return if (merged.size == content.size) {
            result
        } else {
            result.copy(content = JsonObject(merged))
        }
    }

    @Suppress("DEPRECATION", "CyclomaticComplexMethod")
    private fun PrimitiveSchemaDefinition.defaultJsonValue(): JsonElement? = when (this) {
        is StringSchema -> default?.let { JsonPrimitive(it) }

        is IntegerSchema -> default?.let { JsonPrimitive(it) }

        is DoubleSchema -> default?.let { JsonPrimitive(it) }

        is BooleanSchema -> default?.let { JsonPrimitive(it) }

        is UntitledSingleSelectEnumSchema -> default?.let { JsonPrimitive(it) }

        is TitledSingleSelectEnumSchema -> default?.let { JsonPrimitive(it) }

        is LegacyTitledEnumSchema -> default?.let { JsonPrimitive(it) }

        is UntitledMultiSelectEnumSchema -> default?.let { list ->
            buildJsonArray { list.forEach { add(JsonPrimitive(it)) } }
        }

        is TitledMultiSelectEnumSchema -> default?.let { list ->
            buildJsonArray { list.forEach { add(JsonPrimitive(it)) } }
        }
    }

    private fun handleListRoots(): ListRootsResult {
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
                    "Invalid _meta key '$key': prefix labels must start with a letter, end with letter/digit, " +
                        "and contain only letters, digits, or hyphens"
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
                "Invalid _meta key '$key': name must start and end with alphanumeric characters, " +
                    "and contain only alphanumerics, hyphens, underscores, or dots"
            }
        }
    }
}
