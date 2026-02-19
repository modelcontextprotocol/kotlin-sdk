package io.modelcontextprotocol.kotlin.sdk.types

import io.modelcontextprotocol.kotlin.sdk.ExperimentalMcpApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Creates a [ListResourcesRequest] using a type-safe DSL builder.
 *
 * ## Optional
 * - [cursor][ListResourcesRequestBuilder.cursor] - Pagination cursor for fetching next page
 * - [meta][ListResourcesRequestBuilder.meta] - Metadata for the request
 *
 * Example without pagination:
 * ```kotlin
 * val request = ListResourcesRequest { }
 * ```
 *
 * Example with pagination:
 * ```kotlin
 * val request = ListResourcesRequest {
 *     cursor = "eyJwYWdlIjogMn0="
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the list resources request
 * @return A configured [ListResourcesRequest] instance
 * @see ListResourcesRequestBuilder
 * @see ListResourcesRequest
 */
@ExperimentalMcpApi
public inline operator fun ListResourcesRequest.Companion.invoke(
    block: ListResourcesRequestBuilder.() -> Unit,
): ListResourcesRequest = ListResourcesRequestBuilder().apply(block).build()

/**
 * Creates a [ListResourcesRequest] using a type-safe DSL builder.
 *
 * ## Optional
 * - [cursor][ListResourcesRequestBuilder.cursor] - Pagination cursor for fetching next page
 * - [meta][ListResourcesRequestBuilder.meta] - Metadata for the request
 *
 * Example without pagination:
 * ```kotlin
 * val request = buildListResourcesRequest { }
 * ```
 *
 * Example with pagination:
 * ```kotlin
 * val request = buildListResourcesRequest {
 *     cursor = "eyJwYWdlIjogMn0="
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the list resources request
 * @return A configured [ListResourcesRequest] instance
 * @see ListResourcesRequestBuilder
 * @see ListResourcesRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
@Deprecated(
    message = "Use ListResourcesRequest { } instead",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("ListResourcesRequest{apply(block)}"),
)
public inline fun buildListResourcesRequest(block: ListResourcesRequestBuilder.() -> Unit): ListResourcesRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ListResourcesRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [ListResourcesRequest] instances.
 *
 * This builder retrieves a list of available resources, with optional pagination support.
 * All fields are optional.
 *
 * ## Optional
 * - [cursor] - Pagination cursor (inherited from [PaginatedRequestBuilder])
 * - [meta] - Metadata for the request (inherited from [RequestBuilder])
 *
 * @see ListResourcesRequest
 * @see PaginatedRequestBuilder
 */
@McpDsl
public class ListResourcesRequestBuilder @PublishedApi internal constructor() : PaginatedRequestBuilder() {
    @PublishedApi
    override fun build(): ListResourcesRequest {
        val params = paginatedRequestParams(cursor = cursor, meta = meta)
        return ListResourcesRequest(params)
    }
}

/**
 * Creates a [ReadResourceRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [uri][ReadResourceRequestBuilder.uri] - The URI of the resource to read
 *
 * ## Optional
 * - [meta][ReadResourceRequestBuilder.meta] - Metadata for the request
 *
 * Example:
 * ```kotlin
 * val request = ReadResourceRequest {
 *     uri = "file:///path/to/resource.txt"
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the read resource request
 * @return A configured [ReadResourceRequest] instance
 * @see ReadResourceRequestBuilder
 * @see ReadResourceRequest
 */
@ExperimentalMcpApi
public inline operator fun ReadResourceRequest.Companion.invoke(
    block: ReadResourceRequestBuilder.() -> Unit,
): ReadResourceRequest = ReadResourceRequestBuilder().apply(block).build()

/**
 * Creates a [ReadResourceRequest] using a type-safe DSL builder.
 *
 * ## Required
 * - [uri][ReadResourceRequestBuilder.uri] - The URI of the resource to read
 *
 * ## Optional
 * - [meta][ReadResourceRequestBuilder.meta] - Metadata for the request
 *
 * Example:
 * ```kotlin
 * val request = buildReadResourceRequest {
 *     uri = "file:///path/to/resource.txt"
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the read resource request
 * @return A configured [ReadResourceRequest] instance
 * @see ReadResourceRequestBuilder
 * @see ReadResourceRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
@Deprecated(
    message = "Use ReadResourceRequest { } instead",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("ReadResourceRequest{apply(block)}"),
)
public inline fun buildReadResourceRequest(block: ReadResourceRequestBuilder.() -> Unit): ReadResourceRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ReadResourceRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [ReadResourceRequest] instances.
 *
 * This builder reads the contents of a specific resource by URI.
 *
 * ## Required
 * - [uri] - The URI of the resource to read
 *
 * ## Optional
 * - [meta] - Metadata for the request
 *
 * @see ReadResourceRequest
 */
@McpDsl
public class ReadResourceRequestBuilder @PublishedApi internal constructor() : RequestBuilder() {
    /**
     * The URI of the resource to read. This is a required field.
     *
     * Example: `uri = "file:///path/to/resource.txt"`
     */
    public var uri: String? = null

    @PublishedApi
    override fun build(): ReadResourceRequest {
        val uri = requireNotNull(uri) {
            "Missing required field 'uri'. Example: uri = \"file:///path/to/resource.txt\""
        }

        val params = ReadResourceRequestParams(uri = uri, meta = meta)
        return ReadResourceRequest(params)
    }
}

/**
 * Creates a [ListResourceTemplatesRequest] using a type-safe DSL builder.
 *
 * ## Optional
 * - [cursor][ListResourceTemplatesRequestBuilder.cursor] - Pagination cursor for fetching next page
 * - [meta][ListResourceTemplatesRequestBuilder.meta] - Metadata for the request
 *
 * Example without pagination:
 * ```kotlin
 * val request = ListResourceTemplatesRequest { }
 * ```
 *
 * Example with pagination:
 * ```kotlin
 * val request = ListResourceTemplatesRequest {
 *     cursor = "eyJwYWdlIjogMn0="
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the list resource templates request
 * @return A configured [ListResourceTemplatesRequest] instance
 * @see ListResourceTemplatesRequestBuilder
 * @see ListResourceTemplatesRequest
 */
@ExperimentalMcpApi
public inline operator fun ListResourceTemplatesRequest.Companion.invoke(
    block: ListResourceTemplatesRequestBuilder.() -> Unit,
): ListResourceTemplatesRequest = ListResourceTemplatesRequestBuilder().apply(block).build()

/**
 * Creates a [ListResourceTemplatesRequest] using a type-safe DSL builder.
 *
 * ## Optional
 * - [cursor][ListResourceTemplatesRequestBuilder.cursor] - Pagination cursor for fetching next page
 * - [meta][ListResourceTemplatesRequestBuilder.meta] - Metadata for the request
 *
 * Example without pagination:
 * ```kotlin
 * val request = buildListResourceTemplatesRequest { }
 * ```
 *
 * Example with pagination:
 * ```kotlin
 * val request = buildListResourceTemplatesRequest {
 *     cursor = "eyJwYWdlIjogMn0="
 * }
 * ```
 *
 * @param block Configuration lambda for setting up the list resource templates request
 * @return A configured [ListResourceTemplatesRequest] instance
 * @see ListResourceTemplatesRequestBuilder
 * @see ListResourceTemplatesRequest
 */
@OptIn(ExperimentalContracts::class)
@ExperimentalMcpApi
@Deprecated(
    message = "Use ListResourceTemplatesRequest { } instead",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("ListResourceTemplatesRequest{apply(block)}"),
)
public inline fun buildListResourceTemplatesRequest(
    block: ListResourceTemplatesRequestBuilder.() -> Unit,
): ListResourceTemplatesRequest {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ListResourceTemplatesRequestBuilder().apply(block).build()
}

/**
 * DSL builder for constructing [ListResourceTemplatesRequest] instances.
 *
 * This builder retrieves a list of available resource templates, with optional pagination support.
 * All fields are optional.
 *
 * ## Optional
 * - [cursor] - Pagination cursor (inherited from [PaginatedRequestBuilder])
 * - [meta] - Metadata for the request (inherited from [RequestBuilder])
 *
 * @see ListResourceTemplatesRequest
 * @see PaginatedRequestBuilder
 */
@McpDsl
public class ListResourceTemplatesRequestBuilder @PublishedApi internal constructor() : PaginatedRequestBuilder() {
    @PublishedApi
    override fun build(): ListResourceTemplatesRequest {
        val params = paginatedRequestParams(cursor = cursor, meta = meta)
        return ListResourceTemplatesRequest(params)
    }
}

// ============================================================================
// Result Builders (Server-side)
// ============================================================================

/**
 * Creates a [ListResourcesResult] using a type-safe DSL builder.
 *
 * Example:
 * ```kotlin
 * val result = ListResourcesResult {
 *     resource {
 *         uri = "file:///path/to/file.txt"
 *         name = "file.txt"
 *         description = "A text file"
 *         mimeType = "text/plain"
 *     }
 * }
 * ```
 */
@ExperimentalMcpApi
public inline operator fun ListResourcesResult.Companion.invoke(
    block: ListResourcesResultBuilder.() -> Unit,
): ListResourcesResult = ListResourcesResultBuilder().apply(block).build()

/**
 * DSL builder for constructing [ListResourcesResult] instances.
 */
@McpDsl
public class ListResourcesResultBuilder @PublishedApi internal constructor() : PaginatedResultBuilder() {
    private val resourcesList: MutableList<Resource> = mutableListOf()

    public fun resource(resource: Resource) {
        resourcesList.add(resource)
    }

    public fun resource(block: ResourceBuilder.() -> Unit) {
        resourcesList.add(ResourceBuilder().apply(block).build())
    }

    @PublishedApi
    override fun build(): ListResourcesResult {
        require(resourcesList.isNotEmpty()) {
            "At least one resource is required. Use resource() or resource { } to add resources."
        }

        return ListResourcesResult(
            resources = resourcesList.toList(),
            nextCursor = nextCursor,
            meta = meta,
        )
    }
}

/**
 * DSL builder for constructing [Resource] instances.
 */
@McpDsl
public class ResourceBuilder @PublishedApi internal constructor() {
    public var uri: String? = null
    public var name: String? = null
    public var description: String? = null
    public var mimeType: String? = null
    public var size: Long? = null
    public var title: String? = null
    public var annotations: Annotations? = null
    public var icons: List<Icon>? = null
    private var metaValue: JsonObject? = null

    public fun meta(meta: JsonObject) {
        this.metaValue = meta
    }

    public fun meta(block: JsonObjectBuilder.() -> Unit) {
        meta(buildJsonObject(block))
    }

    @PublishedApi
    internal fun build(): Resource {
        val uri = requireNotNull(uri) { "Missing required field 'uri'" }
        val name = requireNotNull(name) { "Missing required field 'name'" }

        return Resource(
            uri = uri,
            name = name,
            description = description,
            mimeType = mimeType,
            size = size,
            title = title,
            annotations = annotations,
            icons = icons,
            meta = metaValue,
        )
    }
}

/**
 * Creates a [ReadResourceResult] using a type-safe DSL builder.
 */
@ExperimentalMcpApi
public inline operator fun ReadResourceResult.Companion.invoke(
    block: ReadResourceResultBuilder.() -> Unit,
): ReadResourceResult = ReadResourceResultBuilder().apply(block).build()

/**
 * DSL builder for constructing [ReadResourceResult] instances.
 */
@McpDsl
public class ReadResourceResultBuilder @PublishedApi internal constructor() : ResultBuilder() {
    private val contentsList: MutableList<ResourceContents> = mutableListOf()

    public fun textContent(uri: String, text: String, mimeType: String? = null) {
        contentsList.add(TextResourceContents(text = text, uri = uri, mimeType = mimeType))
    }

    public fun blobContent(uri: String, blob: String, mimeType: String? = null) {
        contentsList.add(BlobResourceContents(blob = blob, uri = uri, mimeType = mimeType))
    }

    public fun content(content: ResourceContents) {
        contentsList.add(content)
    }

    @PublishedApi
    override fun build(): ReadResourceResult {
        require(contentsList.isNotEmpty()) {
            "At least one content block is required. Use textContent(), blobContent(), or content()."
        }

        return ReadResourceResult(
            contents = contentsList.toList(),
            meta = meta,
        )
    }
}

/**
 * Creates a [ListResourceTemplatesResult] using a type-safe DSL builder.
 */
@ExperimentalMcpApi
public inline operator fun ListResourceTemplatesResult.Companion.invoke(
    block: ListResourceTemplatesResultBuilder.() -> Unit,
): ListResourceTemplatesResult = ListResourceTemplatesResultBuilder().apply(block).build()

/**
 * DSL builder for constructing [ListResourceTemplatesResult] instances.
 */
@McpDsl
public class ListResourceTemplatesResultBuilder @PublishedApi internal constructor() : PaginatedResultBuilder() {
    private val templatesList: MutableList<ResourceTemplate> = mutableListOf()

    public fun template(template: ResourceTemplate) {
        templatesList.add(template)
    }

    public fun template(block: ResourceTemplateBuilder.() -> Unit) {
        templatesList.add(ResourceTemplateBuilder().apply(block).build())
    }

    @PublishedApi
    override fun build(): ListResourceTemplatesResult {
        require(templatesList.isNotEmpty()) {
            "At least one template is required. Use template() or template { } to add templates."
        }

        return ListResourceTemplatesResult(
            resourceTemplates = templatesList.toList(),
            nextCursor = nextCursor,
            meta = meta,
        )
    }
}

/**
 * DSL builder for constructing [ResourceTemplate] instances.
 */
@McpDsl
public class ResourceTemplateBuilder @PublishedApi internal constructor() {
    public var uriTemplate: String? = null
    public var name: String? = null
    public var description: String? = null
    public var mimeType: String? = null
    public var title: String? = null
    public var annotations: Annotations? = null
    public var icons: List<Icon>? = null
    private var metaValue: JsonObject? = null

    public fun meta(meta: JsonObject) {
        this.metaValue = meta
    }

    public fun meta(block: JsonObjectBuilder.() -> Unit) {
        meta(buildJsonObject(block))
    }

    @PublishedApi
    internal fun build(): ResourceTemplate {
        val uriTemplate = requireNotNull(uriTemplate) { "Missing required field 'uriTemplate'" }
        val name = requireNotNull(name) { "Missing required field 'name'" }

        return ResourceTemplate(
            uriTemplate = uriTemplate,
            name = name,
            description = description,
            mimeType = mimeType,
            title = title,
            annotations = annotations,
            icons = icons,
            meta = metaValue,
        )
    }
}
