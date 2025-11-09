package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * This request is sent from the client to the server when it first connects,
 * asking it to begin initialization.
 *
 * The initialize request is the first message exchanged in the MCP protocol handshake.
 * It allows the client to communicate its capabilities and protocol version to the server,
 * and receive the server's capabilities in response.
 *
 * @property params The initialization parameters including protocol version and client capabilities.
 */
@Serializable
public data class InitializeRequest(override val params: InitializeRequestParams) : ClientRequest {
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    override val method: Method = Method.Defined.Initialize
}

/**
 * Parameters for an initialize request.
 *
 * @property protocolVersion The latest version of the Model Context Protocol that the client supports.
 * The client MAY decide to support older versions as well.
 * The server will respond with its preferred version, which may differ.
 * @property capabilities The capabilities that this client supports.
 * Describes which optional features the client has implemented.
 * @property clientInfo Information about the client implementation, including name, version, and branding.
 * @property meta Optional metadata for this request. May include a progressToken for
 * out-of-band progress notifications.
 */
@Serializable
public data class InitializeRequestParams(
    val protocolVersion: String,
    val capabilities: ClientCapabilities,
    val clientInfo: Implementation,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams

/**
 * After receiving an [InitializeRequest] from the client, the server sends this response.
 *
 * The server communicates its chosen protocol version, capabilities, and optional
 * instructions for how to effectively use its features.
 *
 * @property protocolVersion The version of the Model Context Protocol that the server wants to use.
 * This may not match the version that the client requested.
 * If the client cannot support this version, it MUST disconnect.
 * @property capabilities The capabilities that this server supports.
 * Describes which optional features the server has implemented.
 * @property serverInfo Information about the server implementation, including name, version, and branding.
 * @property instructions Optional instructions describing how to use the server and its features.
 * Clients can use this to improve the LLM's understanding of available
 * tools, resources, etc. It can be thought of as a "hint" to the model.
 * For example, this information MAY be added to the system prompt to help
 * the LLM make better use of the server's capabilities.
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class InitializeResult(
    val protocolVersion: String = LATEST_PROTOCOL_VERSION,
    val capabilities: ServerCapabilities,
    val serverInfo: Implementation,
    val instructions: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ServerResult
