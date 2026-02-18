package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi

/**
 * Creates an [InitializeRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [protocolVersion][InitializeRequestBuilder.protocolVersion] - MCP protocol version
 * - [capabilities][InitializeRequestBuilder.capabilities] - Client capabilities
 * - [info][InitializeRequestBuilder.info] - Client implementation information
 *
 * ## Optional
 * - [meta][InitializeRequestBuilder.meta] - Metadata for the request
 *
 * Example:
 * ```kotlin
 * val request = InitializeRequest {
 *     protocolVersion = "2024-11-05"
 *     capabilities {
 *         sampling(ClientCapabilities.sampling)
 *         roots(listChanged = true)
 *     }
 *     info("MyClient", "1.0.0")
 * }
 * ```
 *
 * Example with full client info:
 * ```kotlin
 * val request = InitializeRequest {
 *     protocolVersion = "2024-11-05"
 *     capabilities {
 *         sampling(ClientCapabilities.sampling)
 *         experimental {
 *             put("feature", JsonPrimitive(true))
 *         }
 *     }
 *     info(
 *         name = "MyAdvancedClient",
 *         version = "2.0.0",
 *         title = "Advanced MCP Client",
 *         websiteUrl = "https://example.com"
 *     )
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the initialize request
 * @return A configured [InitializeRequest] instance
 * @see InitializeRequestBuilder
 * @see InitializeRequest
 */
@ExperimentalMcpApi
public inline operator fun InitializeRequest.Companion.invoke(
    block: InitializeRequestBuilder.() -> Unit,
): InitializeRequest = InitializeRequestBuilder().apply(block).build()

/**
 * DSL builder for constructing [InitializeRequest] instances.
 *
 * This builder creates the initial handshake request to establish
 * an MCP protocol connection between client and server.
 *
 * ## Required
 * - [protocolVersion] - MCP protocol version string
 * - [capabilities] - Client capabilities configuration
 * - [info] - Client implementation details
 *
 * ## Optional
 * - [meta] - Metadata for the request
 *
 * @see InitializeRequest
 */
@McpDsl
public class InitializeRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    /**
     * The MCP protocol version. This is a required field.
     *
     * Example: `protocolVersion = "2024-11-05"`
     */
    public var protocolVersion: String? = null

    private var capabilities: ClientCapabilities? = null
    private var info: Implementation? = null

    /**
     * Sets client capabilities directly.
     *
     * Example:
     * ```kotlin
     * capabilities(ClientCapabilities(
     *     sampling = ClientCapabilities.sampling,
     *     roots = ClientCapabilities.Roots(listChanged = true)
     * ))
     * ```
     *
     * @param value The [ClientCapabilities] instance
     */
    public fun capabilities(value: ClientCapabilities) {
        capabilities = value
    }

    /**
     * Sets client capabilities using a DSL builder.
     *
     * This is the recommended way to configure capabilities.
     *
     * Example:
     * ```kotlin
     * capabilities {
     *     sampling(ClientCapabilities.sampling)
     *     roots(listChanged = true)
     *     elicitation(ClientCapabilities.elicitation)
     * }
     * ```
     *
     * @param block Lambda for building capabilities
     * @see ClientCapabilitiesBuilder
     */
    public fun capabilities(block: ClientCapabilitiesBuilder.() -> Unit) {
        capabilities = ClientCapabilitiesBuilder().apply(block).build()
    }

    /**
     * Sets client implementation info directly.
     *
     * Example:
     * ```kotlin
     * info(Implementation(
     *     name = "MyClient",
     *     version = "1.0.0"
     * ))
     * ```
     *
     * @param value The [Implementation] instance
     */
    public fun info(value: Implementation) {
        info = value
    }

    /**
     * Sets client implementation info using individual parameters.
     *
     * This is the recommended way to configure client info.
     *
     * Example with required fields only:
     * ```kotlin
     * info("MyClient", "1.0.0")
     * ```
     *
     * Example with all fields:
     * ```kotlin
     * info(
     *     name = "MyClient",
     *     version = "2.0.0",
     *     title = "My MCP Client",
     *     websiteUrl = "https://example.com",
     *     icons = listOf(Icon(
     *         src = "https://example.com/icon.png",
     *         mimeType = "image/png"
     *     ))
     * )
     * ```
     *
     * @param name Client implementation name
     * @param version Client version string
     * @param title Optional human-readable title
     * @param websiteUrl Optional URL to client website
     * @param icons Optional list of client icons
     */
    public fun info(
        name: String,
        version: String,
        title: String? = null,
        websiteUrl: String? = null,
        icons: List<Icon>? = null,
    ) {
        info = Implementation(name = name, version = version, title = title, websiteUrl = websiteUrl, icons = icons)
    }

    @PublishedApi
    override fun build(): InitializeRequest {
        val version = requireNotNull(protocolVersion) {
            "Missing required field 'protocolVersion'. Example: protocolVersion = \"2024-11-05\""
        }
        val capabilities = requireNotNull(capabilities) {
            "Missing required field 'capabilities'. Use capabilities { ... }"
        }
        val info = requireNotNull(info) {
            "Missing required field 'info'. Use info(\"clientName\", \"1.0.0\")"
        }

        val params = InitializeRequestParams(
            protocolVersion = version,
            capabilities = capabilities,
            clientInfo = info,
            meta = meta,
        )
        return InitializeRequest(params = params)
    }
}

// ============================================================================
// Result Builders (Server-side)
// ============================================================================

/**
 * Creates an [InitializeResult] using a type-safe DSL builder.
 *
 * Example:
 * ```kotlin
 * val result = InitializeResult {
 *     protocolVersion = "2024-11-05"
 *     capabilities {
 *         prompts(listChanged = true)
 *         resources(subscribe = true, listChanged = true)
 *         tools(listChanged = true)
 *     }
 *     info("MyServer", "1.0.0")
 *     instructions = "Use this server for..."
 * }
 * ```
 */
@ExperimentalMcpApi
public inline operator fun InitializeResult.Companion.invoke(
    block: InitializeResultBuilder.() -> Unit,
): InitializeResult = InitializeResultBuilder().apply(block).build()

/**
 * DSL builder for constructing [InitializeResult] instances.
 */
@McpDsl
public class InitializeResultBuilder @PublishedApi internal constructor() : ResultBuilder() {
    public var protocolVersion: String = LATEST_PROTOCOL_VERSION
    private var capabilitiesValue: ServerCapabilities? = null
    private var serverInfoValue: Implementation? = null
    public var instructions: String? = null

    public fun capabilities(capabilities: ServerCapabilities) {
        this.capabilitiesValue = capabilities
    }

    public fun info(
        name: String,
        version: String,
        title: String? = null,
        websiteUrl: String? = null,
        icons: List<Icon>? = null,
    ) {
        serverInfoValue = Implementation(
            name = name,
            version = version,
            title = title,
            websiteUrl = websiteUrl,
            icons = icons,
        )
    }

    public fun info(info: Implementation) {
        this.serverInfoValue = info
    }

    @PublishedApi
    override fun build(): InitializeResult {
        val capabilities = requireNotNull(capabilitiesValue) {
            "Missing required field 'capabilities'. " +
                "Use capabilities(ServerCapabilities(...)) to set server capabilities."
        }
        val serverInfo = requireNotNull(serverInfoValue) {
            "Missing required field 'info'. Use info(\"serverName\", \"1.0.0\")"
        }

        return InitializeResult(
            protocolVersion = protocolVersion,
            capabilities = capabilities,
            serverInfo = serverInfo,
            instructions = instructions,
            meta = meta,
        )
    }
}
