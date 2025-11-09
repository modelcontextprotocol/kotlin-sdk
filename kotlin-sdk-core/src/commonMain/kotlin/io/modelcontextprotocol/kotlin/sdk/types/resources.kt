@file:OptIn(ExperimentalSerializationApi::class)

package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
public sealed interface ResourceLike : WithMeta

/**
 * A known resource that the server is capable of reading.
 *
 * Resources represent data sources such as files, database entries, API responses,
 * or other structured data that can be read by clients.
 *
 * @property uri The URI of this resource. Can use any protocol scheme (file://, http://, etc.).
 * @property name The programmatic identifier for this resource.
 * Intended for logical use and API identification. If [title] is not provided,
 * this should be used as a fallback display name.
 * @property description A description of what this resource represents.
 * Clients can use this to improve the LLM's understanding of available resources.
 * It can be thought of like a "hint" to the model.
 * @property mimeType The MIME type of this resource, if known (e.g., "text/plain", "application/json", "image/png").
 * @property size The size of the raw resource content in bytes (i.e., before base64 encoding or any tokenization), if known.
 * Hosts can use this to display file sizes and estimate context window usage.
 * @property title Optional human-readable display name for this resource.
 * Intended for UI and end-user contexts, optimized to be easily understood
 * even by those unfamiliar with domain-specific terminology.
 * If not provided, [name] should be used for display purposes.
 * @property annotations Optional annotations for the client. Provides additional metadata and hints
 * about how to use or display this resource.
 * @property icons Optional set of sized icons that clients can display in their user interface.
 * Clients MUST support at least PNG and JPEG formats.
 * Clients SHOULD also support SVG and WebP formats.
 * @property meta Optional metadata for this resource.
 */
@Serializable
public data class Resource(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val title: String? = null,
    val annotations: Annotations? = null,
    val icons: List<Icon>? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ResourceLike

/**
 * A template description for resources available on the server.
 *
 * Resource templates allow servers to expose parameterized resources that clients can
 * instantiate with specific values. Templates use RFC 6570 URI template syntax,
 * where parameters are indicated with curly braces (e.g., `file:///{directory}/{filename}`).
 *
 * @property uriTemplate A URI template (according to RFC 6570) that can be used to construct resource URIs.
 * Parameters are indicated with curly braces, e.g., `file:///{path}` or `db://users/{userId}`.
 * @property name The programmatic identifier for this template.
 * Intended for logical use and API identification. If [title] is not provided,
 * this should be used as a fallback display name.
 * @property description A description of what this template is for.
 * Clients can use this to improve the LLM's understanding of available resources.
 * It can be thought of like a "hint" to the model.
 * @property mimeType The MIME type for all resources that match this template.
 * This should only be included if all resources matching this template have the same type.
 * For example, a file template might not have a MIME type since files can be of any type,
 * but a database record template might always return JSON.
 * @property title Optional human-readable display name for this template.
 * Intended for UI and end-user contexts, optimized to be easily understood
 * even by those unfamiliar with domain-specific terminology.
 * If not provided, [name] should be used for display purposes.
 * @property annotations Optional annotations for the client. Provides additional metadata and hints
 * about how to use or display resources created from this template.
 * @property icons Optional set of sized icons that clients can display in their user interface.
 * Clients MUST support at least PNG and JPEG formats.
 * Clients SHOULD also support SVG and WebP formats.
 * @property meta Optional metadata for this template.
 */
@Serializable
public data class ResourceTemplate(
    val uriTemplate: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null,
    val title: String? = null,
    val annotations: Annotations? = null,
    val icons: List<Icon>? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : WithMeta

/**
 * A reference to a resource or resource template definition.
 *
 * Used in completion requests and other contexts where a resource needs to be referenced
 * without including its full definition. The URI can be either a specific resource URI
 * or a URI template pattern.
 *
 * @property uri The URI or URI template of the resource.
 * Can be a specific resource URI (e.g., `file:///home/user/doc.txt`)
 * or a URI template with parameters (e.g., `file:///{path}`).
 */
@Serializable
public data class ResourceTemplateReference(val uri: String) : Reference {
    @EncodeDefault
    public override val type: ReferenceType = ReferenceType.ResourceTemplate
}

/**
 * The contents of a specific resource or sub-resource.
 *
 * @property uri The URI of this resource.
 * @property mimeType The MIME type of this resource, if known.
 * @property meta Optional metadata for this response.
 */
@Serializable(with = ResourceContentsPolymorphicSerializer::class)
public sealed interface ResourceContents : WithMeta {
    public val uri: String
    public val mimeType: String?
}

/**
 * Represents the text contents of a resource.
 *
 * @property text The text of the item.
 * This must only be set if the item can actually be represented as text (not binary data).
 * @property uri The URI of this resource.
 * @property mimeType The MIME type of this resource, if known.
 */
@Serializable
public data class TextResourceContents(
    val text: String,
    override val uri: String,
    override val mimeType: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ResourceContents

/**
 * The contents of a specific resource or sub-resource.
 *
 * @property blob A base64-encoded string representing the binary data of the item.
 * @property uri The URI of this resource.
 * @property mimeType The MIME type of this resource, if known.
 */
@Serializable
public data class BlobResourceContents(
    val blob: String,
    override val uri: String,
    override val mimeType: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ResourceContents

/**
 * Represents resource contents with unknown or unspecified data.
 *
 * @property uri The URI of this resource.
 * @property mimeType The MIME type of this resource, if known.
 */
@Serializable
public data class UnknownResourceContents(
    override val uri: String,
    override val mimeType: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ResourceContents

// ============================================================================
// resources/list
// ============================================================================

/**
 * Sent from the client to request a list of resources the server has.
 *
 * Resources are data sources that the server can provide access to, such as files,
 * database entries, API responses, or other structured data.
 *
 * @property params Optional pagination parameters to control which page of results to return.
 */
@Serializable
public data class ListResourcesRequest(override val params: PaginatedRequestParams? = null) :
    ClientRequest,
    PaginatedRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.ResourcesList
}

/**
 * The server's response to a [ListResourcesRequest] from the client.
 *
 * Returns the available resources along with pagination information if there are more results.
 *
 * @property resources The list of available resources. Each resource includes its URI, name,
 * optional description, and MIME type information.
 * @property nextCursor An opaque token representing the pagination position after the last returned result.
 * If present, there may be more results available. The client can pass this token
 * in a subsequent request to fetch the next page.
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class ListResourcesResult(
    val resources: List<Resource>,
    override val nextCursor: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ServerResult,
    PaginatedResult

// ============================================================================
// resources/read
// ============================================================================

/**
 * Parameters for a resources/read request.
 *
 * @property uri The URI of the resource to read. The URI can use any protocol;
 *              it is up to the server how to interpret it. Common schemes include
 *              file://, http://, https://, or custom application-specific schemes.
 * @property meta Optional metadata for this request. May include a progressToken for
 *                out-of-band progress notifications.
 */
@Serializable
public data class ReadResourceRequestParams(
    val uri: String,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams

/**
 * Sent from the client to the server to read a specific resource URI.
 *
 * The server will return the resource's contents, which can be either text or binary data.
 *
 * @property params The parameters specifying which resource URI to read.
 */
@Serializable
public data class ReadResourceRequest(override val params: ReadResourceRequestParams) : ClientRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.ResourcesRead
}

/**
 * The server's response to a [ReadResourceRequest] from the client.
 *
 * Contains the resource contents, which can be a mix of text and binary data.
 * A single resource can return multiple content blocks if it contains multiple parts.
 *
 * @property contents The contents of the resource. Can include text content (with MIME type and text data)
 * or binary/blob content (with MIME type and Base64-encoded data).
 * Multiple content blocks can be returned for resources with multiple parts.
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class ReadResourceResult(
    val contents: List<ResourceContents>,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ServerResult

// ============================================================================
// resources/subscribe
// ============================================================================

/**
 * Parameters for a resources/subscribe request.
 *
 * @property uri The URI of the resource to subscribe to. The URI can use any protocol;
 * it is up to the server how to interpret it.
 * @property meta Optional metadata for this request. May include a progressToken for
 * out-of-band progress notifications.
 */
@Serializable
public data class SubscribeRequestParams(
    val uri: String,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams

/**
 * Sent from the client to request resources/updated notifications from the server
 * whenever a particular resource changes.
 *
 * After subscribing, the server will send [ResourceUpdatedNotification] messages
 * whenever the subscribed resource is modified. This requires the server to support
 * the `subscribe` capability in [ServerCapabilities.resources].
 *
 * @property params The parameters specifying which resource URI to subscribe to.
 */
@Serializable
public data class SubscribeRequest(override val params: SubscribeRequestParams) : ClientRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.ResourcesSubscribe
}

// ============================================================================
// resources/unsubscribe
// ============================================================================

/**
 * Parameters for a resources/unsubscribe request.
 *
 * @property uri The URI of the resource to unsubscribe from. This should match
 * a URI from a previous [SubscribeRequest].
 * @property meta Optional metadata for this request. May include a progressToken for
 * out-of-band progress notifications.
 */
@Serializable
public data class UnsubscribeRequestParams(
    val uri: String,
    @SerialName("_meta")
    override val meta: RequestMeta? = null,
) : RequestParams

/**
 * Sent from the client to request cancellation of resources/updated notifications from the server.
 *
 * This should follow a previous [SubscribeRequest]. After unsubscribing, the server will
 * stop sending [ResourceUpdatedNotification] messages for this resource.
 *
 * @property params The parameters specifying which resource URI to unsubscribe from.
 */
@Serializable
public data class UnsubscribeRequest(override val params: UnsubscribeRequestParams) : ClientRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.ResourcesUnsubscribe
}

// ============================================================================
// resources/templates/list
// ============================================================================

/**
 * Sent from the client to request a list of resource templates the server has.
 *
 * Resource templates are parameterized resource URIs that can be instantiated with
 * specific argument values. For example, a template like `file:///{path}` allows
 * clients to construct URIs for any file path.
 *
 * @property params Optional pagination parameters to control which page of results to return.
 */
@Serializable
public data class ListResourceTemplatesRequest(override val params: PaginatedRequestParams? = null) :
    ClientRequest,
    PaginatedRequest {
    @EncodeDefault
    override val method: Method = Method.Defined.ResourcesTemplatesList
}

/**
 * The server's response to a [ListResourceTemplatesRequest] from the client.
 *
 * Returns the available resource templates along with pagination information if there are more results.
 *
 * @property resourceTemplates The list of available resource templates. Each template includes
 * its URI template pattern, name, optional description, and information
 * about the arguments it accepts.
 * @property nextCursor An opaque token representing the pagination position after the last returned result.
 * If present, there may be more results available. The client can pass this token
 * in a subsequent request to fetch the next page.
 * @property meta Optional metadata for this response.
 */
@Serializable
public data class ListResourceTemplatesResult(
    val resourceTemplates: List<ResourceTemplate>,
    override val nextCursor: String? = null,
    @SerialName("_meta")
    override val meta: JsonObject? = null,
) : ServerResult,
    PaginatedResult
