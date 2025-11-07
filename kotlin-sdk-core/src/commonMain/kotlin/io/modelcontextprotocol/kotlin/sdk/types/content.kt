@file:OptIn(ExperimentalSerializationApi::class)

package io.modelcontextprotocol.kotlin.sdk.types

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
public sealed interface ContentBlock : WithMeta {
    public val type: String
}

@Serializable
public sealed interface MediaContent : ContentBlock

/**
 * Text provided to or from an LLM.
 *
 * @property text The text content of the message.
 * @property annotations Optional annotations for the client.
 * @property meta property/parameter is reserved by MCP to allow clients and servers
 * to attach additional metadata to their interactions.
 */
@Serializable
public data class TextContent(
    val text: String,
    val annotations: Annotations? = null,
    override val meta: JsonObject? = null,
) : MediaContent {
    @EncodeDefault
    public override val type: String = "text"
}

/**
 * An image provided to or from an LLM.
 *
 * @property data The base64-encoded image data.
 * @property mimeType The MIME type of the image. Different providers may support different image types.
 * @property annotations Optional annotations for the client.
 * @property meta property/parameter is reserved by MCP to allow clients and servers
 * to attach additional metadata to their interactions.
 */
@Serializable
public data class ImageContent(
    val data: String,
    val mimeType: String,
    val annotations: Annotations? = null,
    override val meta: JsonObject? = null,
) : MediaContent {
    @EncodeDefault
    public override val type: String = "image"
}

/**
 * Audio provided to or from an LLM.
 *
 * @property data The base64-encoded audio data.
 * @property mimeType The MIME type of the audio. Different providers may support different audio types.
 * @property annotations Optional annotations for the client.
 * @property meta property/parameter is reserved by MCP to allow clients and servers
 * to attach additional metadata to their interactions.
 */
@Serializable
public data class AudioContent(
    val data: String,
    val mimeType: String,
    val annotations: Annotations? = null,
    override val meta: JsonObject? = null,
) : MediaContent {
    @EncodeDefault
    public override val type: String = "audio"
}

/**
 * A resource that the server is capable of reading, included in a prompt or tool call result.
 *
 * Note: resource links returned by tools are not guaranteed to appear in the results of `resources/list` requests.
 *
 * @property name Intended for programmatic or logical use
 * but used as a display name in past specs or fallback (if the title isn’t present).
 * @property uri The URI of this resource.
 * @property title Intended for UI and end-user contexts — optimized to be human-readable and easily understood,
 * even by those unfamiliar with domain-specific terminology.
 *
 * If not provided, the name should be used for display
 * (except for Tool, where `annotations.title` should be given precedence over using `name`, if present).
 * @property size The size of the raw resource content, in bytes (i.e., before base64 encoding or any tokenization),
 * if known.
 *
 * Hosts can use this to display file sizes and estimate context window usage.
 * @property mimeType The MIME type of this resource, if known.
 * @property icons Optional set of sized icons that clients can display in their user interface.
 * See [Icon] for supported formats and requirements.
 * @property description A description of what this resource represents.
 *
 * Clients can use this to improve the LLM’s understanding of available resources.
 * It can be thought of as a "hint" to the model.
 * @property annotations Optional annotations for the client.
 * @property meta property/parameter is reserved by MCP to allow clients and servers
 * to attach additional metadata to their interactions.
 */
@Serializable
public data class ResourceLink(
    val name: String,
    val uri: String,
    val title: String? = null,
    val size: Long? = null,
    val mimeType: String? = null,
    val icons: List<Icon>? = null,
    val description: String? = null,
    val annotations: Annotations? = null,
    override val meta: JsonObject? = null,
) : ContentBlock,
    ResourceLike {
    @EncodeDefault
    public override val type: String = "resource_link"
}

/**
 * The contents of a resource, embedded into a prompt or tool call result.
 *
 * It is up to the client how best to render embedded resources for the benefit of the LLM and/or the user.
 *
 * @property resource The resource contents.
 * @property annotations Optional annotations for the client.
 * @property meta property/parameter is reserved by MCP to allow clients and servers
 * to attach additional metadata to their interactions.
 */
@Serializable
public data class EmbeddedResource(
    val resource: ResourceContents,
    val annotations: Annotations? = null,
    override val meta: JsonObject? = null,
) : ContentBlock {
    @EncodeDefault
    public override val type: String = "resource"
}
