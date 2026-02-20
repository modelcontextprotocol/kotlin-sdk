package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Creates a [CallToolRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [name][CallToolRequestBuilder.name] - The name of the tool to call
 *
 * ## Optional
 * - [arguments][CallToolRequestBuilder.arguments] - Arguments to pass to the tool
 * - [meta][CallToolRequestBuilder.meta] - Metadata for the request
 *
 * Example without arguments:
 * ```kotlin
 * val request = CallToolRequest {
 *     name = "getCurrentTime"
 * }
 * ```
 *
 * Example with arguments:
 * ```kotlin
 * val request = CallToolRequest {
 *     name = "searchDatabase"
 *     arguments {
 *         put("query", "users")
 *         put("limit", 10)
 *     }
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the call tool request
 * @return A configured [CallToolRequest] instance
 * @see CallToolRequestBuilder
 * @see CallToolRequest
 */
@ExperimentalMcpApi
public inline operator fun CallToolRequest.Companion.invoke(block: CallToolRequestBuilder.() -> Unit): CallToolRequest =
    CallToolRequestBuilder().apply(block).build()

/**
 * Creates a [CallToolRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [name][CallToolRequestBuilder.name] - The name of the tool to call
 *
 * ## Optional
 * - [arguments][CallToolRequestBuilder.arguments] - Arguments to pass to the tool
 * - [meta][CallToolRequestBuilder.meta] - Metadata for the request
 *
 * Example without arguments:
 * ```kotlin
 * val request = buildCallToolRequest {
 *     name = "getCurrentTime"
 * }
 * ```
 *
 * Example with arguments:
 * ```kotlin
 * val request = buildCallToolRequest {
 *     name = "searchDatabase"
 *     arguments {
 *         put("query", "users")
 *         put("limit", 10)
 *     }
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the call tool request
 * @return A configured [CallToolRequest] instance
 * @see CallToolRequestBuilder
 * @see CallToolRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
@Deprecated(
    message = "Use CallToolRequest { } instead",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("CallToolRequest{apply(block)}"),
)
public inline fun buildCallToolRequest(block: CallToolRequestBuilder.() -> Unit): CallToolRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return CallToolRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [CallToolRequest] instances.
 *
 * This builder invokes a specific tool by name with optional arguments.
 *
 * ## Required
 * - [name] - The name of the tool to call
 *
 * ## Optional
 * - [arguments] - Arguments to pass to the tool
 * - [meta] - Metadata for the request
 *
 * @see CallToolRequest
 */
@McpDsl
public class CallToolRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    /**
     * The name of the tool to call. This is a required field.
     *
     * Example: `name = "getCurrentTime"`
     */
    public var name: String? = null

    private var arguments: JsonObject? = null

    /**
     * Sets tool arguments directly from a JsonObject.
     *
     * Example:
     * ```kotlin
     * arguments(buildJsonObject {
     *     put("query", "users")
     * })
     * ```
     */
    public fun arguments(arguments: JsonObject) {
        this.arguments = arguments
    }

    /**
     * Sets tool arguments using a DSL builder.
     *
     * This is the recommended way to provide tool arguments.
     *
     * Example:
     * ```kotlin
     * arguments {
     *     put("query", "SELECT * FROM users")
     *     put("limit", 100)
     *     put("includeDeleted", false)
     * }
     * ```
     *
     * @param block Lambda for building the arguments JsonObject
     */
    public fun arguments(block: JsonObjectBuilder.() -> Unit): Unit = arguments(buildJsonObject(block))

    @PublishedApi
    override fun build(): CallToolRequest {
        val name = requireNotNull(name) {
            "Missing required field 'name'. Example: name = \"toolName\""
        }

        val params = CallToolRequestParams(name = name, arguments = arguments, meta = meta)
        return CallToolRequest(params)
    }
}

/**
 * Creates a [ListToolsRequest] using a type-safe DSL builder.
 *
 * ## Optional
 * - [cursor][ListToolsRequestBuilder.cursor] - Pagination cursor for fetching next page
 * - [meta][ListToolsRequestBuilder.meta] - Metadata for the request
 *
 * Example without pagination:
 * ```kotlin
 * val request = ListToolsRequest { }
 * ```
 *
 * Example with pagination:
 * ```kotlin
 * val request = ListToolsRequest {
 *     cursor = "eyJwYWdlIjogMn0="
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the list tools request
 * @return A configured [ListToolsRequest] instance
 * @see ListToolsRequestBuilder
 * @see ListToolsRequest
 */
@ExperimentalMcpApi
public inline operator fun ListToolsRequest.Companion.invoke(
    block: ListToolsRequestBuilder.() -> Unit,
): ListToolsRequest = ListToolsRequestBuilder().apply(block).build()

/**
 * Creates a [ListToolsRequest] using a type-safe DSL builder.
 *
 * ## Optional
 * - [cursor][ListToolsRequestBuilder.cursor] - Pagination cursor for fetching next page
 * - [meta][ListToolsRequestBuilder.meta] - Metadata for the request
 *
 * Example without pagination:
 * ```kotlin
 * val request = buildListToolsRequest { }
 * ```
 *
 * Example with pagination:
 * ```kotlin
 * val request = buildListToolsRequest {
 *     cursor = "eyJwYWdlIjogMn0="
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the list tools request
 * @return A configured [ListToolsRequest] instance
 * @see ListToolsRequestBuilder
 * @see ListToolsRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
@Deprecated(
    message = "Use ListToolsRequest { } instead",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("ListToolsRequest{apply(block)}"),
)
public inline fun buildListToolsRequest(block: ListToolsRequestBuilder.() -> Unit): ListToolsRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ListToolsRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [ListToolsRequest] instances.
 *
 * This builder retrieves a list of available tools, with optional pagination support.
 * All fields are optional.
 *
 * ## Optional
 * - [cursor] - Pagination cursor (inherited from [PaginatedRequestBuilder])
 * - [meta] - Metadata for the request (inherited from [RequestBuilder])
 *
 * @see ListToolsRequest
 * @see PaginatedRequestBuilder
 */
@McpDsl
public class ListToolsRequestBuilder @PublishedApi internal constructor() : PaginatedRequestBuilder() {
    @PublishedApi
    override fun build(): ListToolsRequest {
        val params = paginatedRequestParams(cursor = cursor, meta = meta)
        return ListToolsRequest(params)
    }
}

// ============================================================================
// Result Builders (Server-side)
// ============================================================================

/**
 * Creates a [CallToolResult] using a type-safe DSL builder.
 *
 * ## Required
 * - [content][CallToolResultBuilder.content] - List of content blocks (at least one)
 *
 * ## Optional
 * - [isError][CallToolResultBuilder.isError] - Whether the tool call resulted in an error
 * - [structuredContent][CallToolResultBuilder.structuredContent] - Machine-readable structured output
 * - [meta][CallToolResultBuilder.meta] - Metadata for the response
 *
 * Example success response:
 * ```kotlin
 * val result = CallToolResult {
 *     textContent("Operation completed successfully")
 * }
 * ```
 *
 * Example error response:
 * ```kotlin
 * val result = CallToolResult {
 *     textContent("Failed to connect to database")
 *     isError = true
 * }
 * ```
 *
 * Example with structured content:
 * ```kotlin
 * val result = CallToolResult {
 *     textContent("Query returned 3 results")
 *     structuredContent {
 *         put("count", 3)
 *         putJsonArray("results") {
 *             add("result1")
 *             add("result2")
 *             add("result3")
 *         }
 *     }
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the call tool result
 * @return A configured [CallToolResult] instance
 * @see CallToolResultBuilder
 * @see CallToolResult
 */
@ExperimentalMcpApi
public inline operator fun CallToolResult.Companion.invoke(block: CallToolResultBuilder.() -> Unit): CallToolResult =
    CallToolResultBuilder().apply(block).build()

/**
 * DSL builder for constructing [CallToolResult] instances.
 *
 * This builder creates the server's response to a tool call, including the result content
 * and optional error status.
 *
 * ## Required
 * - At least one content block (via [textContent], [imageContent], [audioContent], or [content])
 *
 * ## Optional
 * - [isError] - Whether the tool call resulted in an error
 * - [structuredContent] - Machine-readable structured output
 * - [meta] - Metadata for the response
 *
 * ## Implementation Notes
 *
 * **Mutability:** This builder uses mutable collections during construction for efficient accumulation.
 * The [build] method creates defensive copies (`.toList()`) to ensure the returned [CallToolResult]
 * is immutable and safe to share.
 *
 * **Nullability:** Optional fields use nullable types (`Boolean?`, `JsonObject?`) rather than defaults
 * to avoid serializing default values in the MCP protocol. When these fields are `null`, they are
 * omitted from the JSON output, reducing message size and following protocol conventions.
 *
 * @see CallToolResult
 */
@McpDsl
public class CallToolResultBuilder @PublishedApi internal constructor() : ResultBuilder() {
    private val contentList: MutableList<ContentBlock> = mutableListOf()

    /**
     * Whether the tool call resulted in an error.
     *
     * When true, the content should describe the error that occurred.
     * If not set, this is assumed to be false (success).
     *
     * **Design note:** This field is nullable rather than defaulting to `false` to avoid
     * serializing the default value in the JSON protocol. When `null`, the field is omitted
     * from the serialized output, reducing message size and following MCP protocol conventions.
     *
     * Example: `isError = true`
     */
    public var isError: Boolean? = null

    private var structuredContentValue: JsonObject? = null

    /**
     * Adds a text content block to the result.
     *
     * This is the most common content type for tool results.
     *
     * Example:
     * ```kotlin
     * textContent("Operation completed successfully")
     * ```
     *
     * @param text The text content
     */
    public fun textContent(text: String) {
        contentList.add(TextContent(text = text))
    }

    /**
     * Adds an image content block to the result.
     *
     * Example:
     * ```kotlin
     * imageContent(
     *     data = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ...",
     *     mimeType = "image/png"
     * )
     * ```
     *
     * @param data Base64-encoded image data
     * @param mimeType The MIME type of the image (e.g., "image/png", "image/jpeg")
     */
    public fun imageContent(data: String, mimeType: String) {
        contentList.add(ImageContent(data = data, mimeType = mimeType))
    }

    /**
     * Adds an audio content block to the result.
     *
     * Example:
     * ```kotlin
     * audioContent(
     *     data = "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgA...",
     *     mimeType = "audio/wav"
     * )
     * ```
     *
     * @param data Base64-encoded audio data
     * @param mimeType The MIME type of the audio (e.g., "audio/wav", "audio/mp3")
     */
    public fun audioContent(data: String, mimeType: String) {
        contentList.add(AudioContent(data = data, mimeType = mimeType))
    }

    /**
     * Adds a pre-built content block to the result.
     *
     * Use this when you have already constructed a content block.
     *
     * Example:
     * ```kotlin
     * val block = TextContent("Prebuilt content")
     * content(block)
     * ```
     *
     * @param block The content block to add
     */
    public fun content(block: ContentBlock) {
        contentList.add(block)
    }

    /**
     * Sets structured content directly from a JsonObject.
     *
     * **Note:** Prefer using the DSL lambda variant [structuredContent] for more idiomatic Kotlin code.
     * This overload is provided for cases where you already have a constructed JsonObject.
     *
     * Example:
     * ```kotlin
     * val existingData = buildJsonObject {
     *     put("status", "success")
     *     put("recordsAffected", 5)
     * }
     * structuredContent(existingData)
     * ```
     *
     * @param content The structured content as a JsonObject
     * @see structuredContent
     */
    public fun structuredContent(content: JsonObject) {
        this.structuredContentValue = content
    }

    /**
     * Sets structured content using a DSL builder.
     *
     * **This is the preferred way to set structured content.** The DSL syntax is more idiomatic
     * and integrates better with Kotlin's type-safe builders.
     *
     * Provides machine-readable output in addition to the human-readable content.
     *
     * Example (preferred):
     * ```kotlin
     * structuredContent {
     *     put("status", "success")
     *     put("count", 42)
     *     putJsonArray("items") {
     *         add("item1")
     *         add("item2")
     *     }
     * }
     * ```
     *
     * @param block Lambda for building the structured content JsonObject
     */
    public fun structuredContent(block: JsonObjectBuilder.() -> Unit) {
        structuredContent(buildJsonObject(block))
    }

    @PublishedApi
    override fun build(): CallToolResult {
        require(contentList.isNotEmpty()) {
            "At least one content block is required. Use textContent(), imageContent(), or audioContent()."
        }

        return CallToolResult(
            content = contentList.toList(),
            isError = isError,
            structuredContent = structuredContentValue,
            meta = meta,
        )
    }
}

/**
 * Creates a [ListToolsResult] using a type-safe DSL builder.
 *
 * ## Required
 * - [tools][ListToolsResultBuilder.toolsList] - List of available tools (at least one)
 *
 * ## Optional
 * - [nextCursor][ListToolsResultBuilder.nextCursor] - Pagination cursor for next page
 * - [meta][ListToolsResultBuilder.meta] - Metadata for the response
 *
 * Example with single tool:
 * ```kotlin
 * val result = ListToolsResult {
 *     tool {
 *         name = "searchDatabase"
 *         description = "Search the database for records"
 *         inputSchema {
 *             put("type", "object")
 *             putJsonObject("properties") {
 *                 putJsonObject("query") {
 *                     put("type", "string")
 *                     put("description", "Search query")
 *                 }
 *             }
 *             putJsonArray("required") {
 *                 add("query")
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * Example with pagination:
 * ```kotlin
 * val result = ListToolsResult {
 *     tool(Tool("tool1", ToolSchema()))
 *     tool(Tool("tool2", ToolSchema()))
 *     nextCursor = "eyJwYWdlIjogMn0="
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the list tools result
 * @return A configured [ListToolsResult] instance
 * @see ListToolsResultBuilder
 * @see ListToolsResult
 */
@ExperimentalMcpApi
public inline operator fun ListToolsResult.Companion.invoke(block: ListToolsResultBuilder.() -> Unit): ListToolsResult =
    ListToolsResultBuilder().apply(block).build()

/**
 * DSL builder for constructing [ListToolsResult] instances.
 *
 * This builder creates a response containing a list of tools available on the server,
 * with optional pagination support.
 *
 * ## Required
 * - At least one tool (via [tool] method)
 *
 * ## Optional
 * - [nextCursor] - Pagination cursor (inherited from [PaginatedResultBuilder])
 * - [meta] - Metadata for the response (inherited from [ResultBuilder])
 *
 * @see ListToolsResult
 * @see PaginatedResultBuilder
 */
@McpDsl
public class ListToolsResultBuilder @PublishedApi internal constructor() : PaginatedResultBuilder() {
    private val toolsList: MutableList<Tool> = mutableListOf()

    /**
     * Adds a pre-built tool to the result.
     *
     * Use this when you have already constructed a Tool instance.
     *
     * Example:
     * ```kotlin
     * val myTool = Tool(
     *     name = "calculate",
     *     inputSchema = ToolSchema(/* ... */),
     *     description = "Performs calculations"
     * )
     * tool(myTool)
     * ```
     *
     * @param tool The tool to add
     */
    public fun tool(tool: Tool) {
        toolsList.add(tool)
    }

    /**
     * Adds a tool using a DSL builder.
     *
     * This is the recommended way to define tools inline.
     *
     * Example:
     * ```kotlin
     * tool {
     *     name = "getCurrentTime"
     *     description = "Get the current time"
     *     inputSchema {
     *         // Schema definition
     *     }
     * }
     * ```
     *
     * @param block Lambda for building the Tool
     */
    public fun tool(block: ToolBuilder.() -> Unit) {
        toolsList.add(ToolBuilder().apply(block).build())
    }

    @PublishedApi
    override fun build(): ListToolsResult {
        require(toolsList.isNotEmpty()) {
            "At least one tool is required. Use tool() or tool { } to add tools."
        }

        return ListToolsResult(
            tools = toolsList.toList(),
            nextCursor = nextCursor,
            meta = meta,
        )
    }
}

/**
 * DSL builder for constructing [Tool] instances.
 *
 * Used within [ListToolsResultBuilder] to define individual tools.
 *
 * ## Required
 * - [name] - The programmatic identifier for the tool
 * - [inputSchema] - JSON Schema defining the tool's input parameters
 *
 * ## Optional
 * - [description] - Human-readable description
 * - [outputSchema] - JSON Schema defining the tool's output structure
 * - [title] - Display name for the tool
 * - [annotations] - Additional hints about tool behavior
 * - [icons] - Icon representations for UIs
 * - [meta] - Metadata for the tool
 *
 * @see Tool
 * @see ListToolsResultBuilder
 */
@McpDsl
public class ToolBuilder @PublishedApi internal constructor() {
    /**
     * The programmatic identifier for this tool. Required.
     *
     * Example: `name = "searchDatabase"`
     */
    public var name: String? = null

    /**
     * Human-readable description of what the tool does.
     *
     * Example: `description = "Search the database for records matching a query"`
     */
    public var description: String? = null

    private var inputSchemaValue: ToolSchema? = null

    private var outputSchemaValue: ToolSchema? = null

    /**
     * Optional display name for the tool.
     *
     * Example: `title = "Database Search"`
     */
    public var title: String? = null

    /**
     * Optional annotations providing hints about tool behavior.
     *
     * Example:
     * ```kotlin
     * annotations = ToolAnnotations(
     *     readOnlyHint = true,
     *     idempotentHint = true
     * )
     * ```
     */
    public var annotations: ToolAnnotations? = null

    /**
     * Optional list of icons for the tool.
     *
     * Example:
     * ```kotlin
     * icons = listOf(
     *     Icon(url = "https://example.com/icon.png", size = "32x32", mimeType = "image/png")
     * )
     * ```
     */
    public var icons: List<Icon>? = null

    private var metaValue: JsonObject? = null

    /**
     * Sets input schema directly from a ToolSchema.
     *
     * Example:
     * ```kotlin
     * inputSchema(ToolSchema(
     *     properties = buildJsonObject { /* ... */ },
     *     required = listOf("query")
     * ))
     * ```
     *
     * @param schema The input schema
     */
    public fun inputSchema(schema: ToolSchema) {
        this.inputSchemaValue = schema
    }

    /**
     * Sets input schema using a DSL builder.
     *
     * Example:
     * ```kotlin
     * inputSchema {
     *     properties = buildJsonObject {
     *         putJsonObject("query") {
     *             put("type", "string")
     *             put("description", "Search query")
     *         }
     *         putJsonObject("limit") {
     *             put("type", "integer")
     *             put("default", 10)
     *         }
     *     }
     *     required = listOf("query")
     * }
     * ```
     *
     * @param block Lambda for building the ToolSchema
     */
    public fun inputSchema(block: ToolSchemaBuilder.() -> Unit) {
        inputSchema(ToolSchemaBuilder().apply(block).build())
    }

    /**
     * Sets output schema directly from a ToolSchema.
     *
     * Example:
     * ```kotlin
     * outputSchema(ToolSchema(
     *     properties = buildJsonObject { /* ... */ }
     * ))
     * ```
     *
     * @param schema The output schema
     */
    public fun outputSchema(schema: ToolSchema) {
        this.outputSchemaValue = schema
    }

    /**
     * Sets output schema using a DSL builder.
     *
     * Example:
     * ```kotlin
     * outputSchema {
     *     properties = buildJsonObject {
     *         putJsonObject("results") {
     *             put("type", "array")
     *         }
     *         putJsonObject("count") {
     *             put("type", "integer")
     *         }
     *     }
     * }
     * ```
     *
     * @param block Lambda for building the ToolSchema
     */
    public fun outputSchema(block: ToolSchemaBuilder.() -> Unit) {
        outputSchema(ToolSchemaBuilder().apply(block).build())
    }

    /**
     * Sets metadata directly from a JsonObject.
     *
     * Example:
     * ```kotlin
     * meta(buildJsonObject {
     *     put("version", "1.0")
     * })
     * ```
     *
     * @param meta The metadata as a JsonObject
     */
    public fun meta(meta: JsonObject) {
        this.metaValue = meta
    }

    /**
     * Sets metadata using a DSL builder.
     *
     * Example:
     * ```kotlin
     * meta {
     *     put("version", "1.0")
     *     put("deprecated", false)
     * }
     * ```
     *
     * @param block Lambda for building the metadata JsonObject
     */
    public fun meta(block: JsonObjectBuilder.() -> Unit) {
        meta(buildJsonObject(block))
    }

    @PublishedApi
    internal fun build(): Tool {
        val name = requireNotNull(name) {
            "Missing required field 'name'. Example: name = \"toolName\""
        }
        val inputSchema = requireNotNull(inputSchemaValue) {
            "Missing required field 'inputSchema'. Use inputSchema { } to define the schema."
        }

        return Tool(
            name = name,
            inputSchema = inputSchema,
            description = description,
            outputSchema = outputSchemaValue,
            title = title,
            annotations = annotations,
            icons = icons,
            meta = metaValue,
        )
    }
}

/**
 * DSL builder for constructing [ToolSchema] instances.
 *
 * Used to define JSON Schema for tool input/output parameters.
 *
 * @see ToolSchema
 * @see ToolBuilder
 */
@McpDsl
public class ToolSchemaBuilder @PublishedApi internal constructor() {
    /**
     * Map of property names to their schema definitions.
     *
     * Example:
     * ```kotlin
     * properties = buildJsonObject {
     *     putJsonObject("query") {
     *         put("type", "string")
     *         put("description", "Search query")
     *     }
     * }
     * ```
     */
    public var properties: JsonObject? = null

    /**
     * List of required property names.
     *
     * Example: `required = listOf("query", "userId")`
     */
    public var required: List<String>? = null

    /**
     * Schema definitions available to references in properties ($defs).
     *
     * Example:
     * ```kotlin
     * defs = buildJsonObject {
     *     putJsonObject("Address") {
     *         put("type", "object")
     *         putJsonObject("properties") {
     *             putJsonObject("street") {
     *                 put("type", "string")
     *             }
     *         }
     *     }
     * }
     * ```
     */
    public var defs: JsonObject? = null

    @PublishedApi
    internal fun build(): ToolSchema = ToolSchema(
        properties = properties,
        required = required,
        defs = defs,
    )
}
