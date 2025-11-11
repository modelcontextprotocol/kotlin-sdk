@file:OptIn(ExperimentalSerializationApi::class)

package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Creates a [CallToolResult] with single [TextContent] and [meta].
 */
public fun CallToolResult.Companion.success(content: String, meta: JsonObject? = null): CallToolResult = CallToolResult(
    content = listOf(TextContent(content)),
    isError = false,
    meta = meta,
)

/**
 * Creates a [CallToolResult] with single [TextContent] and [meta], with `isError` being true.
 */
public fun CallToolResult.Companion.error(content: String, meta: JsonObject? = null): CallToolResult = CallToolResult(
    content = listOf(TextContent(content)),
    isError = true,
    meta = meta,
)

/**
 * Definition for a tool the client can call.
 *
 * Tools allow servers to expose functionality that can be invoked by clients and LLMs.
 * Each tool has a name, description, and schema defining its input (and optionally output) parameters.
 *
 * **Display name precedence order:** [title] → [annotations].[title] → [name]
 *
 * @property name The programmatic identifier for this tool.
 * Intended for logical use and API identification. Used as a display name fallback
 * if both [title] and [annotations].[title] are not provided.
 * @property inputSchema A JSON Schema object defining the expected parameters for the tool.
 * Must be an object type schema. Defines what arguments the tool accepts.
 * @property description A human-readable description of the tool and when to use it.
 * Clients can use this to improve the LLM's understanding of available tools.
 * It can be thought of like a "hint" to the model.
 * @property outputSchema An optional JSON Schema object defining the structure of the tool's output
 * returned in the [structuredContent][CallToolResult.structuredContent] field of a [CallToolResult].
 * Must be an object type schema if provided.
 * @property title Optional human-readable display name for this tool.
 * Intended for UI and end-user contexts, optimized to be easily understood
 * even by those unfamiliar with domain-specific terminology.
 * Note: For Tool specifically, [annotations].[title] takes precedence over this field.
 * @property annotations Optional additional tool information providing hints about tool behavior.
 * All properties in [ToolAnnotations] are hints and not guaranteed to provide
 * a faithful description of tool behavior.
 * @property icons Optional set of sized icons that clients can display in their user interface.
 * Clients MUST support at least PNG and JPEG formats.
 * Clients SHOULD also support SVG and WebP formats.
 * @property meta Optional metadata for this tool.
 */
@Serializable
public data class Tool(
    val name: String,
    val inputSchema: ToolSchema,
    val description: String? = null,
    val outputSchema: ToolSchema? = null,
    val title: String? = null,
    val annotations: ToolAnnotations? = null,
    val icons: List<Icon>? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : WithMeta

/**
 * A JSON Schema for tool input or output parameters.
 *
 * This is a simplified schema structure that must be of type "object".
 *
 * @property type Always "object" for tool schemas.
 * @property properties Optional map of property names to their schema definitions.
 * @property required Optional list of property names that are required.
 */
@Serializable
public data class ToolSchema(val properties: JsonObject? = null, val required: List<String>? = null) {
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val type: String = "object"
}

/**
 * Additional properties describing a Tool to clients.
 *
 * **IMPORTANT:** All properties in ToolAnnotations are **hints**. They are NOT guaranteed to provide
 * a faithful description of tool behavior (including descriptive properties like [title]).
 *
 * **Security warning:** Clients should NEVER make tool use decisions based on ToolAnnotations
 * received from untrusted servers.
 *
 * @property title A human-readable title for the tool.
 * Takes precedence over [Tool.title] and [Tool.name] for display purposes.
 * @property readOnlyHint If true, the tool does not modify its environment.
 * If false, the tool may perform modifications.
 * Default: false
 * @property destructiveHint If true, the tool may perform destructive updates to its environment.
 * If false, the tool performs only additive updates.
 * This property is meaningful only when [readOnlyHint] == false.
 * Default: true
 * @property idempotentHint If true, calling the tool repeatedly with the same arguments will have
 * no additional effect on its environment.
 * This property is meaningful only when [readOnlyHint] == false.
 * Default: false
 * @property openWorldHint If true, this tool may interact with an "open world" of external entities.
 * If false, the tool's domain of interaction is closed.
 * For example, the world of a web search tool is open, whereas that of a
 * memory tool is not.
 * Default: true
 */
@Serializable
public data class ToolAnnotations(
    val title: String? = null,
    val readOnlyHint: Boolean? = null,
    val destructiveHint: Boolean? = null,
    val idempotentHint: Boolean? = null,
    val openWorldHint: Boolean? = null,
)

// ============================================================================
// tools/call
// ============================================================================

/**
 * Used by the client to invoke a tool provided by the server.
 *
 * Tools allow servers to expose functionality that clients (and LLMs) can call.
 * Examples include file operations, API calls, database queries, or any other
 * server-side operations.
 *
 * @property params The parameters specifying which tool to call and what arguments to pass.
 */
@Serializable
public data class CallToolRequest(override val params: CallToolRequestParams) : ClientRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.ToolsCall

    /**
     * The name of the tool to invoke.
     */
    public val name: String
        get() = params.name

    /**
     * Arguments to pass to the tool. Keys are argument names, values are the argument values.
     * The structure must match the tool's input schema.
     */
    public val arguments: JsonObject?
        get() = params.arguments

    /**
     * Metadata for this request. May include a progressToken for out-of-band progress notifications.
     */
    public val meta: RequestMeta?
        get() = params.meta
}

/**
 * Parameters for a tools/call request.
 *
 * @property name The name of the tool to invoke.
 * @property arguments Arguments to pass to the tool. Keys are argument names, values are the argument values.
 * The structure must match the tool's input schema.
 * @property meta Optional metadata for this request. May include a progressToken for
 * out-of-band progress notifications.
 */
@Serializable
public data class CallToolRequestParams(
    val name: String,
    val arguments: JsonObject? = null,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams

/**
 * The server's response to a [CallToolRequest].
 *
 * Contains the result of the tool execution, which can be successful or an error.
 *
 * **Important error handling:**
 * - Errors that originate from the **tool itself** SHOULD be reported inside the result object
 *   with [isError] set to true, NOT as an MCP protocol-level error response. This allows the LLM
 *   to see that an error occurred and potentially self-correct.
 * - Errors in **finding the tool**, unsupported operations, or other exceptional conditions
 *   SHOULD be reported as MCP protocol-level error responses.
 *
 * @property content A list of content blocks that represent the unstructured result of the tool call.
 * This is what the LLM will see as the tool's output.
 * @property isError Whether the tool call ended in an error.
 * If not set, this is assumed to be false (the call was successful).
 * When true, the [content] should describe the error that occurred.
 * @property structuredContent An optional JSON object that represents the structured result of the tool call.
 * Provides machine-readable output in addition to the human-readable [content].
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class CallToolResult(
    val content: List<ContentBlock>,
    val isError: Boolean? = null,
    val structuredContent: JsonObject? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ServerResult

// ============================================================================
// tools/list
// ============================================================================

/**
 * Sent from the client to request a list of tools the server has.
 *
 * This request supports pagination through the cursor parameter. If the server has many
 * tools, it may return a subset along with a cursor to fetch the next page.
 *
 * @property params Optional pagination parameters to control which page of results to return.
 */
@Serializable
public data class ListToolsRequest(override val params: PaginatedRequestParams? = null) :
    ClientRequest,
    PaginatedRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.ToolsList
}

/**
 * The server's response to a [ListToolsRequest] from the client.
 *
 * Returns the available tools along with pagination information if there are more results.
 *
 * @property tools The list of available tools. Each tool includes its name, description,
 * and input schema that defines what arguments it accepts.
 * @property nextCursor An opaque token representing the pagination position after the last returned result.
 * If present, there may be more results available. The client can pass this token
 * in a subsequent request to fetch the next page.
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class ListToolsResult(
    val tools: List<Tool>,
    override val nextCursor: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ServerResult,
    PaginatedResult
